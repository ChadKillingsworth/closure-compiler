/*
 * Copyright 2024 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.integration;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.ConformanceConfig;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for the compiler handling code that is "closure unaware". */
@RunWith(JUnit4.class)
public final class ClosureUnawareCodeIntegrationTest extends IntegrationTestCase {

  /** Creates a CompilerOptions object with google coding conventions. */
  public CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setDevMode(DevMode.EVERY_PASS);
    options.setCodingConvention(new GoogleCodingConvention());
    options.setRenamePrefixNamespaceAssumeCrossChunkNames(true);
    options.setAssumeGettersArePure(false);
    options.setPrettyPrint(true);
    return options;
  }

  @Test
  public void testNoOptimizeClosureUnawareCode_doesntOptimizeClasses() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .build();

    test(
        options,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('a.b');
        /** @closureUnaware */
        (function() {
          class ClazzWithStatic {
            constructor() {}

            /** @nosideeffects */
            static Create() {
              if (Math.random() > .5) {
                throw new Error('Bad input');
              }
              return new ClazzWithStatic();
            }
          }

          const xUnused = ClazzWithStatic.Create();
          globalThis['a_b'] = xUnused;
        }).call(globalThis);
        exports = globalThis['a_b'];
        """,
        """
        (function() {
          class ClazzWithStatic {
            constructor() {}

            /** @nosideeffects */
            static Create() {
              if (Math.random() > .5) {
                throw new Error('Bad input');
              }
              return new ClazzWithStatic();
            }
          }

          const xUnused = ClazzWithStatic.Create();
          globalThis['a_b'] = xUnused;
        }).call(globalThis);
        """);
  }

  @Test
  public void testNoOptimizeClosureUnawareCode_transpilationAndWhitespaceOnly() {
    CompilerOptions options = createCompilerOptions();
    options.setSkipNonTranspilationPasses(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .build();

    // Sadly, we can't use 'test' to validate this, because it will parse the "use strict" pragma
    // in the expected output into a BLOCK with the use_strict property set. This is correct, but
    // the WhitespaceWrapGoogModules pass deliberately does not rewrite code using goog.loadModule
    // this way because BLOCK's with use_strict do not have the pragma printed when code-printed
    // (because it is redundant with the top-level pragma).
    // Instead, do a direct string comparison of the output, and validate there are no errors or
    // warnings during compilation.

    Compiler compiler =
        compile(
            options,
            new String[] {
              """
              /**
               * @fileoverview
               * @closureUnaware
               */
              goog.module('a.b');
              /** @closureUnaware */
              (function() {
                class ClazzWithStatic {
                  constructor() {}

                  /** @nosideeffects */
                  static Create() {
                    if (Math.random() > .5) {
                      throw new Error('Bad input');
                    }
                    return new ClazzWithStatic();
                  }
                }

                const xUnused = ClazzWithStatic.Create();
                globalThis['a_b'] = xUnused;
              }).call(globalThis);
              exports = globalThis['a_b'];
              """,
            });

    Node root = compiler.getRoot().getLastChild();

    // Verify that there are no unexpected errors before checking the compiled output
    assertWithMessage(
            "Expected no warnings or errors\n"
                + "Errors: \n"
                + Joiner.on("\n").join(compiler.getErrors())
                + "\n"
                + "Warnings: \n"
                + Joiner.on("\n").join(compiler.getWarnings()))
        .that(compiler.getErrors().size() + compiler.getWarnings().size())
        .isEqualTo(0);

    assertThat(compiler.toSource(root))
        .isEqualTo(
            """
            goog.loadModule(function(exports) {
              "use strict";
              goog.module("a.b");
              (function() {
                class ClazzWithStatic {
                  constructor() {
                  }
                  static Create() {
                    if (Math.random() > .5) {
                      throw new Error("Bad input");
                    }
                    return new ClazzWithStatic();
                  }
                }
                const xUnused = ClazzWithStatic.Create();
                globalThis["a_b"] = xUnused;
              }).call(globalThis);
              exports = globalThis["a_b"];
              return exports;
            });
            """);
  }

  @Test
  public void testNoOptimizeClosureUnawareCode_doesntRemoveUselessCode() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .build();

    test(
        options,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('a.b');
        /** @closureUnaware */
        (function() {
          const x = 5;
        }).call(globalThis);
        """,
        """
        (function() {
          const x = 5;
        }).call(globalThis);
        """);
  }

  @Test
  public void testNoOptimizeClosureUnawareCode_stripsWhitespaceAndCommentsFromProtectedCode() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);
    // Set to true by default for most tests, defaults to false for most prod builds.
    options.setPrettyPrint(false);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .build();

    // These compile and test steps are mostly copied from `test`, but instead of doing an AST level
    // validation we do a byte-for-byte comparison of the source output.
    String original =
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('a.b');
        /** @closureUnaware */
        (function() {
          // This is a comment that should be removed


        \t

          // and so is this one?
          const x = 5;
        }).call(globalThis);
        """;

    String expected = "'use strict';(function(){const x=5}).call(globalThis);";

    Compiler compiler = compile(options, original);

    // Verify that there are no unexpected errors before checking the compiled output
    assertWithMessage(
            "Expected no warnings or errors\n"
                + "Errors: \n"
                + Joiner.on("\n").join(compiler.getErrors())
                + "\n"
                + "Warnings: \n"
                + Joiner.on("\n").join(compiler.getWarnings()))
        .that(compiler.getErrors().size() + compiler.getWarnings().size())
        .isEqualTo(0);

    assertThat(compiler.toSource()).isEqualTo(expected);
  }

  @Test
  public void testNoOptimizeClosureUnawareCode_removesDeadCodeOutsideSpecialIIFEs() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .build();

    test(
        options,
        """
        /**
         * @fileoverview
         * @closureUnaware
         * @suppress {uselessCode}
         */
        goog.module('a.b');
        if (false) {
          /** @closureUnaware */
          (function() {
            const x = 5;
          }).call(globalThis);
        } else {
          /** @closureUnaware */
          (function() {
            const x = 10;
          }).call(globalThis);
        }
        """,
        """
        (function() {
          const x = 10;
        }).call(globalThis);
        """);
  }

  @Test
  public void
      testNoOptimizeClosureUnawareCode_removesDeadCodeOutsideSpecialIIFEs_invertedCondition() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .build();

    test(
        options,
        """
        /**
         * @fileoverview
         * @closureUnaware
         * @suppress {uselessCode}
         */
        goog.module('a.b');
        if (true) {
          /** @closureUnaware */
          (function() {
            const x = 5;
          }).call(globalThis);
        } else {
          /** @closureUnaware */
          (function() {
            const x = 10;
          }).call(globalThis);
        }
        """,
        """
        (function() {
          const x = 5;
        }).call(globalThis);
        """);
  }

  @Test
  public void
      testNoOptimizeClosureUnawareCode_doesNotHideHumanAuthoredWrapperCallsFromConformance() {
    String balEvalConfig =
        """
        requirement: {
          type: BANNED_NAME_CALL
          value: '$jscomp_wrap_closure_unaware_code'
           error_message: '$jscomp_wrap_closure_unaware_code is not allowed'
        }
        """;

    ConformanceConfig.Builder conformanceBuilder = ConformanceConfig.newBuilder();
    try {
      TextFormat.merge(balEvalConfig, conformanceBuilder);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }

    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);
    options.setConformanceConfig(conformanceBuilder.build());

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .add(
                SourceFile.fromCode(
                    "user_defined_externs.js",
                    """
                    /**
                     * @type {function(string):?}
                     */
                    var $jscomp_wrap_closure_unaware_code;
                    """))
            .build();

    test(
        options,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('a.b');
        $jscomp_wrap_closure_unaware_code('{ const x = 5 }');
        """,
        // This error is currently reported as "invalid structure for @closureUnaware annotated
        // code"
        // but if we loosen the restrictions then this should be replaced with
        // DiagnosticGroups.CONFORMANCE_VIOLATIONS (a conformance violation) instead.
        DiagnosticGroups.INVALID_CLOSURE_UNAWARE_ANNOTATED_CODE);
  }

  @Test
  public void
      testNoOptimizeClosureUnawareCode_doesNotHideHumanAuthoredWrapperCallsFromConformance_notClosureUnaware() {
    String balEvalConfig =
        """
        requirement: {
          type: BANNED_NAME_CALL
          value: '$jscomp_wrap_closure_unaware_code'
           error_message: '$jscomp_wrap_closure_unaware_code is not allowed'
        }
        """;

    ConformanceConfig.Builder conformanceBuilder = ConformanceConfig.newBuilder();
    try {
      TextFormat.merge(balEvalConfig, conformanceBuilder);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }

    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);
    options.setConformanceConfig(conformanceBuilder.build());

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .add(
                SourceFile.fromCode(
                    "user_defined_externs.js",
                    """
                    /**
                     * @type {function(string):?}
                     */
                    var $jscomp_wrap_closure_unaware_code;
                    """))
            .build();

    test(
        options,
        """
        goog.module('a.b');
        $jscomp_wrap_closure_unaware_code('{ const x = 5 }');
        """,
        DiagnosticGroups.CONFORMANCE_VIOLATIONS);
  }

  @Test
  public void testNoOptimizeClosureUnawareCode_parsesCodeWithNonClosureJsDoc() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .build();

    test(
        options,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('a.b');
        /** @closureUnaware */
        (function() {
          /**
           * @prop {number} a - scale x
        // @defaults isn't a valid jsdoc tag, but within  the closure-unaware portions of the
        // AST we should not raise a warning.
           * @defaults
           */
          const x = /** @inline */ (5);
        }).call(globalThis);
        """,
        """
        (function() {
          const x = 5;
        }).call(globalThis);
        """);
  }

  @Test
  public void
      testNoOptimizeClosureUnawareCode_parsesCodeWithNonClosureJsDoc_whenNotAttachedToNode() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .build();

    // this test-case is trying to ensure that even when there are jsdoc comments in the
    // closure-unaware portions of the AST that aren't attached to specific nodes / expressions
    // that we can still ignore invalid jsdoc contents.
    // @defaults isn't a valid jsdoc tag, and outside of the closure-unaware portions of the AST we
    // should raise a warning.

    test(
        options,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('a.b');
        /** @closureUnaware */
        (function() {
          /** @defaults */ // JSDoc comment not attached to any node
          /**
           * @prop {number} a - scale x
           * @defaults
           */
          const x = 5;
        }).call(globalThis);
        """,
        """
        (function() {
          const x = 5;
        }).call(globalThis);
        """);
  }

  @Test
  public void
      testNoOptimizeClosureUnawareCode_parsesCodeWithNonClosureJsDoc_whenNotAttachedToNode_typedJsDoc() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .build();

    // this test-case is trying to ensure that even when there are jsdoc comments in the
    // closure-unaware portions of the AST that aren't attached to specific nodes / expressions
    // that we can still ignore invalid jsdoc contents.
    // @type [string] does not parse as a valid "typed jsdoc" tag, and this follows a different
    // parsing codepath than non-type jsdoc tags and raises a different error
    // than an entirely unknown jsdoc tag.

    test(
        options,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('a.b');
        /** @closureUnaware */
        (function() {
          /** @type [string] */

          /**
           * @prop {number} a - scale x
           * @defaults
           */
          const x = 5;
        }).call(globalThis);
        """,
        """
        (function() {
          const x = 5;
        }).call(globalThis);
        """);
  }

  @Test
  public void
      testNoOptimizeClosureUnawareCode_doesntSuppressParseErrorsOutsideClosureUnawareBlocks() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .build();

    // this test-case is trying to ensure that even when the special handling for JSDoc comments in
    // closureUnaware code we can still report jsdoc errors for code that is not within the
    // closure-unaware subsection of the AST.
    test(
        options,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('a.b');
        // This is invalid JSDoc, but it isn't within the subtree of a node annotated as
        // `@closureUnaware`.
        // NOTE: The `@closureUnaware` in the `@fileoverview` comment does not apply this
        // subtree suppression
        // mechanism to the whole script - it is only to indicate that the file contains some
        // closure-unaware code
        // (e.g. a performance optimization).
        /**
         * @prop {number} a - scale x
         */
        /** @closureUnaware */
        (function() {
          const x = 5;
        }).call(globalThis);
        """,
        DiagnosticGroups.NON_STANDARD_JSDOC);
  }

  @Test
  public void
      testNoOptimizeClosureUnawareCode_doesntSuppressParseErrorsOutsideClosureUnawareBlocks_afterRange() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .build();

    // this test-case is trying to ensure that even when the special handling for JSDoc comments in
    // closureUnaware code we can still report jsdoc errors for code that is not within the
    // closure-unaware subsection of the AST.
    // Specifically this test validates that any comments that come after a closure-unaware
    // subsection of the AST don't have their jsdoc parse errors suppressed.
    test(
        options,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('a.b');
        /** @closureUnaware */
        (function() {
          const x = 5;
        }).call(globalThis);
        /**
         * @prop {number} a - scale x
         */
        """,
        DiagnosticGroups.NON_STANDARD_JSDOC);
  }

  @Test
  public void testNoOptimizeClosureUnawareCode_doesntCreateCastNodesInClosureUnawareBlocks() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .build();

    // Normally, the RemoveCastNodes would remove all the CAST nodes from the AST before it is ever
    // serialized into a TypedAST. We don't want to run RemoveCastNodes over closure-unaware code,
    // so instead this test validates that during parsing we never create CAST nodes to begin with.
    test(
        options,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('a.b');
        /** @closureUnaware */
        (function() {
          const x = /** @type {string | number} */ (5);
        }).call(globalThis);
        """,
        """
        (function() {
          const x = /** string */ (5);
        }).call(globalThis);
        """);

    Node fn =
        lastCompiler
            .getRoot()
            .getSecondChild()
            .getFirstFirstChild()
            .getFirstFirstChild()
            .getFirstChild();
    assertNode(fn).isFunction();
    assertThat(fn.getJSDocInfo()).isNotNull();
    assertThat(fn.getJSDocInfo().isClosureUnawareCode()).isTrue();
    NodeTraversal.builder()
        .setCompiler(lastCompiler)
        .setCallback((NodeTraversal t, Node n, Node parent) -> assertThat(n.isCast()).isFalse())
        .traverse(fn);
  }

  @Test
  public void testNoOptimizeClosureUnawareCode_doesntCreateJSDocInfoWithinClosureUnawareBlocks() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    options.setPreserveNonJSDocComments(true);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .build();

    // Normally, the RemoveCastNodes would remove all the CAST nodes from the AST before it is ever
    // serialized into a TypedAST. We don't want to run RemoveCastNodes over closure-unaware code,
    // so instead this test validates that during parsing we never create CAST nodes to begin with.
    test(
        options,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('a.b');
        /** @closureUnaware */
        (function() {
          /**
        // This @date tag is ignored - it isn't parsed as JSDoc and doesn't end up in the
        // license text.
           * @date 1900-01-01
           * @license FOO BAR BAZ!
           */
          const z = /** hello world! */ (5);
          const x = /** @type {string | number} */ (5);
        }).call(globalThis);
        """,
        """
        (function() {
          const z = 5; const x = 5;
        }).call(globalThis);
        """);

    Node script = lastCompiler.getRoot().getSecondChild().getFirstChild();
    assertNode(script).isScript();
    assertThat(script.getJSDocInfo()).isNotNull();
    assertThat(script.getJSDocInfo().getLicense()).isEqualTo(" FOO BAR BAZ!\n");

    Node fn = script.getFirstChild().getFirstFirstChild().getFirstChild();
    assertNode(fn).isFunction();
    assertThat(fn.getJSDocInfo()).isNotNull();
    assertThat(fn.getJSDocInfo().isClosureUnawareCode()).isTrue();

    Node fnBlock = fn.getChildAtIndex(2);
    assertNode(fnBlock).isBlock();

    // From within the expected closure-unaware code block, we want to ensure that no AST node has
    // any jsdocinfo attached to it.
    // instead, the relevant nodes should have nonjsdoc comments attached to them.
    List<Node> nodesWithNonJsdocComments = new ArrayList<>();

    NodeTraversal.builder()
        .setCompiler(lastCompiler)
        .setCallback(
            (NodeTraversal t, Node n, Node parent) -> {
              if (n.getNonJSDocComment() != null) {
                nodesWithNonJsdocComments.add(n);
              }

              JSDocInfo info = n.getJSDocInfo();
              assertThat(info).isNull();
            })
        .traverse(fnBlock);

    assertThat(
            nodesWithNonJsdocComments.stream().map(n -> n.getNonJSDocComment().getCommentString()))
        .containsExactly("/** hello world! */", "/** @type {string | number} */");
  }

  @Test
  public void testNoOptimizeClosureUnawareCode_collapsesSequentialComments() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);

    options.setPreserveNonJSDocComments(true);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "globalthis.js",
                    """
                    /**
                     * @type {?}
                     */
                    var globalThis;
                    """))
            .build();

    // Normally, the RemoveCastNodes would remove all the CAST nodes from the AST before it is ever
    // serialized into a TypedAST. We don't want to run RemoveCastNodes over closure-unaware code,
    // so instead this test validates that during parsing we never create CAST nodes to begin with.
    test(
        options,
        """
        /**
         * @fileoverview
         * @closureUnaware
         */
        goog.module('a.b');
        /** @closureUnaware */
        (function() {
          // A B C
          /** FOO BAR BAZ! */
          // D E F
          const z = 5;
        }).call(globalThis);
        """,
        """
        (function() {
          const z = 5;
        }).call(globalThis);
        """);

    Node script = lastCompiler.getRoot().getSecondChild().getFirstChild();
    assertNode(script).isScript();

    Node fn = script.getFirstChild().getFirstFirstChild().getFirstChild();
    assertNode(fn).isFunction();
    assertThat(fn.getJSDocInfo()).isNotNull();
    assertThat(fn.getJSDocInfo().isClosureUnawareCode()).isTrue();

    Node fnBlock = fn.getChildAtIndex(2);
    assertNode(fnBlock).isBlock();

    // From within the expected closure-unaware code block, we want to ensure that no AST node has
    // any jsdocinfo attached to it.
    // instead, the relevant nodes should have nonjsdoc comments attached to them.
    List<Node> nodesWithNonJsdocComments = new ArrayList<>();

    NodeTraversal.builder()
        .setCompiler(lastCompiler)
        .setCallback(
            (NodeTraversal t, Node n, Node parent) -> {
              if (n.getNonJSDocComment() != null) {
                nodesWithNonJsdocComments.add(n);
              }

              JSDocInfo info = n.getJSDocInfo();
              assertThat(info).isNull();
            })
        .traverse(fnBlock);

    assertThat(
            nodesWithNonJsdocComments.stream().map(n -> n.getNonJSDocComment().getCommentString()))
        .containsExactly("// A B C\n/** FOO BAR BAZ! */\n// D E F");
  }

  // TODO how can I test whether source info is being properly retained?
  // TODO if there is a sourcemap comment in the IIFE, can we use that info somehow?
}
