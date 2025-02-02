/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.fir.utils

import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.idea.fir.low.level.api.api.FirModuleResolveState
import org.jetbrains.kotlin.idea.fir.low.level.api.api.withFirDeclaration
import org.jetbrains.kotlin.idea.fir.low.level.api.lazy.resolve.ResolveType
import org.jetbrains.kotlin.idea.frontend.api.ValidityTokenOwner
import org.jetbrains.kotlin.idea.frontend.api.tokens.ValidityToken
import org.jetbrains.kotlin.idea.frontend.api.tokens.assertIsValidAndAccessible
import java.lang.ref.WeakReference

internal class FirRefWithValidityCheck<out D : FirDeclaration>(fir: D, resolveState: FirModuleResolveState, val token: ValidityToken) {
    private val firWeakRef = WeakReference(fir)
    private val resolveStateWeakRef = WeakReference(resolveState)

    @TestOnly
    internal fun isCollected(): Boolean =
        firWeakRef.get() == null && resolveStateWeakRef.get() == null

    inline fun <R> withFir(phase: FirResolvePhase = FirResolvePhase.RAW_FIR, crossinline action: (fir: D) -> R): R {
        token.assertIsValidAndAccessible()
        val fir = firWeakRef.get()
            ?: throw EntityWasGarbageCollectedException("FirElement")
        val resolveState = resolveStateWeakRef.get()
            ?: throw EntityWasGarbageCollectedException("FirModuleResolveState")
        return fir.withFirDeclaration(resolveState, phase) { action(it) }
    }

    /**
     * Runs [action] with fir element *without* any lock hold
     * Consider using this only when you are completely sure
     * that fir or one of it's container already holds the lock (i.e, corresponding withFir call was made)
     */
    inline fun <R> withFirUnsafe(action: (fir: D) -> R): R {
        token.assertIsValidAndAccessible()
        val fir = firWeakRef.get()
            ?: throw EntityWasGarbageCollectedException("FirElement")
        return action(fir)
    }

    val resolveState
        get() = resolveStateWeakRef.get() ?: throw EntityWasGarbageCollectedException("FirModuleResolveState")

    inline fun <R> withFirAndCache(phase: FirResolvePhase = FirResolvePhase.RAW_FIR, crossinline createValue: (fir: D) -> R) =
        ValidityAwareCachedValue(token) {
            withFir(phase) { fir -> createValue(fir) }
        }

    inline fun <R> withFirByType(type: ResolveType, crossinline action: (fir: D) -> R): R {
        token.assertIsValidAndAccessible()
        val fir = firWeakRef.get()
            ?: throw EntityWasGarbageCollectedException("FirElement")
        val resolveState = resolveStateWeakRef.get()
            ?: throw EntityWasGarbageCollectedException("FirModuleResolveState")
        return fir.withFirDeclaration(type, resolveState) { action(it) }
    }

    inline fun <R> withFirAndCache(type: ResolveType, crossinline createValue: (fir: D) -> R) =
        ValidityAwareCachedValue(token) {
            withFirByType(type) { fir -> createValue(fir) }
        }

    override fun equals(other: Any?): Boolean {
        if (other !is FirRefWithValidityCheck<*>) return false
        return getRefFir() == other.getRefFir() && this.token == other.token
    }

    override fun hashCode(): Int {
        return getRefFir().hashCode() * 31 + token.hashCode()
    }

    private fun getRefFir(): FirDeclaration {
        return firWeakRef.get() ?: throw EntityWasGarbageCollectedException("FirElement")
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun <D : FirDeclaration> ValidityTokenOwner.firRef(fir: D, resolveState: FirModuleResolveState) =
    FirRefWithValidityCheck(fir, resolveState, token)
