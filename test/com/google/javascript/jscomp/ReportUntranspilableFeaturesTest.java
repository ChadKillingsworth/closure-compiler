/*
 * Copyright 2014 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp;

import static com.google.javascript.jscomp.CheckRegExp.MALFORMED_REGEXP;
import static com.google.javascript.jscomp.ReportUntranspilableFeatures.UNTRANSPILABLE_FEATURE_PRESENT;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.BrowserFeaturesetYear;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ReportUntranspilableFeaturesTest extends CompilerTestCase {

  private LanguageMode languageOut;
  private @Nullable BrowserFeaturesetYear browserFeaturesetYear;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    languageOut = LanguageMode.ECMASCRIPT3;
    browserFeaturesetYear = null;
    disableTypeCheck();
    enableRunTypeCheckAfterProcessing();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return PeepholeTranspilationsPass.create(
        compiler,
        ImmutableList.of(
            new ReportUntranspilableFeatures(
                compiler,
                browserFeaturesetYear,
                (browserFeaturesetYear != null
                    ? browserFeaturesetYear.getFeatureSet()
                    : languageOut.toFeatureSet()))));
  }

  @Test
  public void testEs2018RegexFlagS() {
    languageOut = LanguageMode.ECMASCRIPT_2018;
    testSame("const a = /asdf/;");
    testSame("const a = /asdf/g;");
    testSame("const a = /asdf/s;");
    testSame("const a = /asdf/gs;");

    languageOut = LanguageMode.ECMASCRIPT_2017;
    testSame("const a = /asdf/;");
    testSame("const a = /asdf/g;");
    testError(
        "const a = /asdf/s;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        """
        Cannot convert feature "RegExp flag 's'" to targeted output language. Feature requires\
         at minimum ECMASCRIPT_2018. Consider targeting a more modern output.\
        """);
    testError(
        "const a = /asdf/gs;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        """
        Cannot convert feature "RegExp flag 's'" to targeted output language. Feature requires\
         at minimum ECMASCRIPT_2018. Consider targeting a more modern output.\
        """);
  }

  @Test
  public void testInvalidRegExpReportsWarning() {
    testWarning("const a = /([0-9a-zA-Z_\\-]{20,}/", MALFORMED_REGEXP);
  }

  @Test
  public void testEs2018RegexLookbehind() {
    languageOut = LanguageMode.ECMASCRIPT_2018;
    testSame("const a = /(?<=asdf)/;");
    testSame("const a = /(?<!asdf)/;");

    languageOut = LanguageMode.ECMASCRIPT_2017;
    testSame("const a = /(?=asdf)/;"); // Lookaheads are fine
    testSame("const a = /(?!asdf)/g;"); // Lookaheads are fine
    testError(
        "const a = /(?<=asdf)/;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        """
        Cannot convert feature "RegExp Lookbehind" to targeted output language. Feature requires\
         at minimum ECMASCRIPT_2018. Consider targeting a more modern output.\
        """);
    testError(
        "const a = /(?<!asdf)/;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        """
        Cannot convert feature "RegExp Lookbehind" to targeted output language. Feature requires\
         at minimum ECMASCRIPT_2018. Consider targeting a more modern output.\
        """);
  }

  @Test
  public void testEs2018RegexUnicodePropertyEscape() {
    languageOut = LanguageMode.ECMASCRIPT_2018;
    testSame("const a = /\\p{Script=Greek}/u;");
    testSame("const a = /\\P{Script=Greek}/u;");
    testSame("const a = /\\p{Script=Greek}/;"); // Without u flag, /\p/ is same as /p/
    testSame("const a = /\\P{Script=Greek}/;"); // Without u flag, /\p/ is same as /p/

    languageOut = LanguageMode.ECMASCRIPT_2017;
    testSame("const a = /\\p{Script=Greek}/;"); // Without u flag, /\p/ is same as /p/
    testSame("const a = /\\P{Script=Greek}/;"); // Without u flag, /\p/ is same as /p/
    testError(
        "const a = /\\p{Script=Greek}/u;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        """
        Cannot convert feature "RegExp unicode property escape" to targeted output language.\
         Feature requires at minimum ECMASCRIPT_2018. Consider targeting a more modern\
         output.\
        """);
    testError(
        "const a = /\\P{Script=Greek}/u;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        """
        Cannot convert feature "RegExp unicode property escape" to targeted output language.\
         Feature requires at minimum ECMASCRIPT_2018. Consider targeting a more modern\
         output.\
        """);
  }

  @Test
  public void testRegExpConstructorCalls() {
    languageOut = LanguageMode.ECMASCRIPT_2017;
    // TODO(bradfordcsmith): report errors from RegExp in this form
    testSame("const a = new RegExp('asdf', 'gs');");
  }

  @Test
  public void testEs2018RegexNamedCaptureGroups() {
    languageOut = LanguageMode.ECMASCRIPT_2018;
    testSame("const a = /(?<name>)/u;");

    languageOut = LanguageMode.ECMASCRIPT_2017;
    testError(
        "const a = /(?<name>)/;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        """
        Cannot convert feature "RegExp named groups" to targeted output language. Feature\
         requires at minimum ECMASCRIPT_2018. Consider targeting a more modern output.\
        """);
    testError(
        "const a = /(?<$var>).*/u;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        """
        Cannot convert feature "RegExp named groups" to targeted output language. Feature\
         requires at minimum ECMASCRIPT_2018. Consider targeting a more modern output.\
        """);
    // test valid regex with '<' or '>' that is not named capture group
    testSame("const a = /(<name>)/;");
    testSame("const a = /(>.>)/u;");
  }

  @Test
  public void testEs2018RegexNamedCaptureGroupsBackReferencing() {
    languageOut = LanguageMode.ECMASCRIPT_2018;
    testSame("const a = /^(?<half>.*).\\k<half>$/u;");

    languageOut = LanguageMode.ECMASCRIPT_2017;
    testError(
        "const a = /^(?<half>.*).\\k<half>$/u;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        """
        Cannot convert feature "RegExp named groups" to targeted output language. Feature\
         requires at minimum ECMASCRIPT_2018. Consider targeting a more modern output.\
        """);
  }

  @Test
  public void testEs2018RegexNamedCaptureGroupsBackReferencing_usingBrowserFeaturesetYear() {
    browserFeaturesetYear = BrowserFeaturesetYear.YEAR_2021;
    testSame("const a = /^(?<half>.*).\\k<half>$/u;");

    browserFeaturesetYear = BrowserFeaturesetYear.YEAR_2020;
    testError(
        "const a = /^(?<half>.*).\\k<half>$/u;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        """
        Cannot convert feature "RegExp named groups" to targeted output language. Feature\
         requires at minimum browser featureset year 2021. Consider targeting a more modern\
         output.
        Current browser featureset year: 2020\
        """);
  }

  @Test
  public void reportErrorWithBigIntLiteralTranspilation() {
    languageOut = LanguageMode.ECMASCRIPT_2021;
    testSame("1234n");

    languageOut = LanguageMode.ECMASCRIPT3;
    testError("1234n", ReportUntranspilableFeatures.UNTRANSPILABLE_FEATURE_PRESENT);
  }

  @Test
  public void reportErrorWithBigIntLiteralTranspilation_browserFeaturesetYear() {
    browserFeaturesetYear = BrowserFeaturesetYear.YEAR_2021;
    testSame("1234n");

    browserFeaturesetYear = BrowserFeaturesetYear.YEAR_2020;
    testError(
        "1234n",
        ReportUntranspilableFeatures.UNTRANSPILABLE_FEATURE_PRESENT,
        """
        Cannot convert feature "bigint" to targeted output language. Feature requires at minimum\
         browser featureset year 2021. Consider targeting a more modern output.
        Current browser featureset year: 2020\
        """);
  }

  @Test
  public void noErrorWithBigIntConstructorTranspilation() {
    // Do not report an error for use of the `BigInt()` method, just the literal form.
    languageOut = LanguageMode.ECMASCRIPT3;
    testSame("BigInt(1234)");
  }

  @Test
  public void testEs2022RegexFlagD() {
    languageOut = LanguageMode.ECMASCRIPT_NEXT;
    testSame("const a = /^(?<half>.*).\\k<half>$/d;");

    languageOut = LanguageMode.ECMASCRIPT_2021;
    testError(
        "const a = /^(?<half>.*).\\k<half>$/d;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        """
        Cannot convert feature "RegExp flag 'd'" to targeted output language. Feature requires\
         at minimum ES_NEXT. Consider targeting a more modern output.\
        """);
  }

  @Test
  public void testEs2022RegexFlagD_usingBrowserFeaturesetYear() {
    browserFeaturesetYear = BrowserFeaturesetYear.YEAR_2024;
    testError(
        "const a = /^(?<half>.*).\\k<half>$/d;",
        UNTRANSPILABLE_FEATURE_PRESENT,
        """
        Cannot convert feature "RegExp flag 'd'" to targeted output language. Feature requires\
         at minimum ES_NEXT, which is not yet supported by any browser featureset year.\
        """);
  }
}
