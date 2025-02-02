/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer.mergedtree

import org.jetbrains.kotlin.commonizer.ModulesProvider
import org.jetbrains.kotlin.commonizer.cir.CirEntityId
import org.jetbrains.kotlin.commonizer.mergedtree.ArtificialSupertypes.artificialSupertypes
import org.jetbrains.kotlin.commonizer.utils.CNAMES_STRUCTS_PACKAGE
import org.jetbrains.kotlin.commonizer.utils.OBJCNAMES_CLASSES_PACKAGE
import org.jetbrains.kotlin.commonizer.utils.OBJCNAMES_PROTOCOLS_PACKAGE
import org.jetbrains.kotlin.commonizer.utils.isUnderKotlinNativeSyntheticPackages
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.types.Variance

/** A set of classes and type aliases provided by libraries (either the libraries to commonize, or their dependency libraries)/ */
interface CirProvidedClassifiers {
    fun hasClassifier(classifierId: CirEntityId): Boolean
    fun classifier(classifierId: CirEntityId): CirProvided.Classifier?

    object EMPTY : CirProvidedClassifiers {
        override fun hasClassifier(classifierId: CirEntityId) = false
        override fun classifier(classifierId: CirEntityId): CirProvided.Classifier? = null
    }

    private class CompositeClassifiers(val delegates: List<CirProvidedClassifiers>) : CirProvidedClassifiers {
        override fun hasClassifier(classifierId: CirEntityId) = delegates.any { it.hasClassifier(classifierId) }
        override fun classifier(classifierId: CirEntityId): CirProvided.Classifier? {
            var fallbackReturn: CirProvided.Classifier? = null
            for (delegate in delegates) {
                delegate.classifier(classifierId)?.let { classifier ->
                    if (classifier !== FALLBACK_FORWARD_DECLARATION_CLASS) return classifier
                    else fallbackReturn = classifier
                }
            }
            return fallbackReturn
        }
    }

    companion object {
        internal val FALLBACK_FORWARD_DECLARATION_CLASS =
            CirProvided.RegularClass(emptyList(), emptyList(), Visibilities.Public, ClassKind.CLASS)

        fun of(vararg delegates: CirProvidedClassifiers): CirProvidedClassifiers {
            val unwrappedDelegates: List<CirProvidedClassifiers> = delegates.fold(ArrayList()) { acc, delegate ->
                when (delegate) {
                    EMPTY -> Unit
                    is CompositeClassifiers -> acc.addAll(delegate.delegates)
                    else -> acc.add(delegate)
                }
                acc
            }

            return when (unwrappedDelegates.size) {
                0 -> EMPTY
                1 -> unwrappedDelegates.first()
                else -> CompositeClassifiers(unwrappedDelegates)
            }
        }

        fun by(modulesProvider: ModulesProvider?): CirProvidedClassifiers =
            if (modulesProvider != null) CirProvidedClassifiersByModules.load(modulesProvider) else EMPTY
    }
}

object CirProvided {
    /* Classifiers */
    sealed interface Classifier {
        val typeParameters: List<TypeParameter>
    }

    sealed interface Class : Classifier {
        val visibility: Visibility
        val supertypes: List<Type>
    }

    data class RegularClass(
        override val typeParameters: List<TypeParameter>,
        override val supertypes: List<Type>,
        override val visibility: Visibility,
        val kind: ClassKind
    ) : Class

    data class ExportedForwardDeclarationClass(val syntheticClassId: CirEntityId) : Class {
        init {
            check(syntheticClassId.packageName.isUnderKotlinNativeSyntheticPackages)
        }

        override val typeParameters: List<TypeParameter> get() = emptyList()
        override val visibility: Visibility get() = Visibilities.Public
        override val supertypes: List<Type> = syntheticClassId.artificialSupertypes()
    }

    data class TypeAlias(
        override val typeParameters: List<TypeParameter>,
        val underlyingType: Type
    ) : Classifier

    /* Type parameter */
    data class TypeParameter(val index: Int, val variance: Variance)

    /* Types */
    sealed interface Type {
        val isMarkedNullable: Boolean
    }

    data class TypeParameterType(
        val index: Int,
        override val isMarkedNullable: Boolean
    ) : Type

    data class ClassType(
        val classId: CirEntityId,
        val outerType: ClassType?,
        val arguments: List<TypeProjection>,
        override val isMarkedNullable: Boolean
    ) : Type

    data class TypeAliasType(
        val typeAliasId: CirEntityId,
        val arguments: List<TypeProjection>,
        override val isMarkedNullable: Boolean
    ) : Type

    /* Type projections */
    sealed interface TypeProjection
    object StarTypeProjection : TypeProjection
    data class RegularTypeProjection(val variance: Variance, val type: Type) : TypeProjection
}

/**
 * Analog to "KlibResolvedModuleDescriptorsFactoryImpl.createForwardDeclarationsModule" which also
 * automatically assumes relevant supertypes for forward declarations based upon the package they are in.
 */
private object ArtificialSupertypes {
    private fun createType(classId: String): CirProvided.ClassType {
        return CirProvided.ClassType(
            classId = CirEntityId.create(classId),
            outerType = null, arguments = emptyList(), isMarkedNullable = false
        )
    }

    private val cOpaqueType = listOf(createType("kotlinx/cinterop/COpaque"))
    private val objcObjectBase = listOf(createType("kotlinx/cinterop/ObjCObjectBase"))
    private val objcCObject = listOf(createType("kotlinx/cinterop/ObjCObject"))

    fun CirEntityId.artificialSupertypes(): List<CirProvided.Type> {
        return when (packageName) {
            CNAMES_STRUCTS_PACKAGE -> cOpaqueType
            OBJCNAMES_CLASSES_PACKAGE -> objcObjectBase
            OBJCNAMES_PROTOCOLS_PACKAGE -> objcCObject
            else -> emptyList()
        }
    }
}
