/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.runners.codegen;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.kotlin.test.TargetBackend;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link GenerateNewCompilerTests.kt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("compiler/fir/fir2ir/testData/codegen/box")
@TestDataPath("$PROJECT_ROOT")
public class FirSpecificBlackBoxCodegenTestGenerated extends AbstractFirBlackBoxCodegenTest {
    @Test
    public void testAllFilesPresentInBox() throws Exception {
        KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler/fir/fir2ir/testData/codegen/box"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
    }

    @Test
    @TestMetadata("sample.kt")
    public void testSample() throws Exception {
        runTest("compiler/fir/fir2ir/testData/codegen/box/sample.kt");
    }

    @Nested
    @TestMetadata("compiler/fir/fir2ir/testData/codegen/box/properties")
    @TestDataPath("$PROJECT_ROOT")
    public class Properties {
        @Test
        public void testAllFilesPresentInProperties() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler/fir/fir2ir/testData/codegen/box/properties"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
        }

        @Nested
        @TestMetadata("compiler/fir/fir2ir/testData/codegen/box/properties/backingField")
        @TestDataPath("$PROJECT_ROOT")
        public class BackingField {
            @Test
            public void testAllFilesPresentInBackingField() throws Exception {
                KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler/fir/fir2ir/testData/codegen/box/properties/backingField"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
            }

            @Test
            @TestMetadata("backingFieldVisibility.kt")
            public void testBackingFieldVisibility() throws Exception {
                runTest("compiler/fir/fir2ir/testData/codegen/box/properties/backingField/backingFieldVisibility.kt");
            }

            @Test
            @TestMetadata("independentBackingFieldType.kt")
            public void testIndependentBackingFieldType() throws Exception {
                runTest("compiler/fir/fir2ir/testData/codegen/box/properties/backingField/independentBackingFieldType.kt");
            }

            @Test
            @TestMetadata("overriddenPropertiesWithExplicitBackingFields.kt")
            public void testOverriddenPropertiesWithExplicitBackingFields() throws Exception {
                runTest("compiler/fir/fir2ir/testData/codegen/box/properties/backingField/overriddenPropertiesWithExplicitBackingFields.kt");
            }
        }
    }
}
