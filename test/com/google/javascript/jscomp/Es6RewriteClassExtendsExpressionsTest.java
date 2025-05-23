/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class Es6RewriteClassExtendsExpressionsTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6RewriteClassExtendsExpressions(compiler);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableTypeCheck();
    ignoreWarnings(DiagnosticGroups.CHECK_TYPES);
    enableTypeInfoValidation();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Test
  public void testBasic() {
    test(
        "const foo = {'bar': Object}; /** @extends {Object} */ class Foo extends foo['bar'] {}",
        """
        const foo = {'bar': Object};
        const testcode$classextends$var0 = foo['bar'];
        class Foo extends testcode$classextends$var0 {}
        """);
  }

  @Test
  public void testName() {
    testSame("class Foo extends Object {}");
  }

  @Test
  public void testGetProp() {
    testSame("const foo = { bar: Object}; class Foo extends foo.bar {}");
  }

  @Test
  public void testMixinFunction() {
    test(
        """
        /** @return {function(new:Object)} */
        function mixObject(Superclass) {
          return class extends Superclass {
            bar() { return 'bar'; }
          };
        }
        class Foo {}
        /** @extends {Object} */
        class Bar extends mixObject(Foo) {}
        """,
        """
        function mixObject(Superclass) {
          return class extends Superclass {
            bar() { return 'bar'; }
          };
        }
        class Foo {}
        const testcode$classextends$var0 = mixObject(Foo);
        class Bar extends testcode$classextends$var0 {}
        """);
  }

  @Test
  public void testClassExpressions() {
    testSame(
        """
        const foo = { bar: Object};
        function baz(arg) {}
        baz(class extends foo.bar {});
        """);
  }

  @Test
  public void testClassExtendsClassTranspilation() {
    String code =
        """
class __PRIVATE_WebChannelConnection extends class __PRIVATE_RestConnection {
  constructor(e) {
    this.databaseInfo = e, this.databaseId = e.databaseId;
  }
} {
  constructor(e) {
    super(e), this.forceLongPolling = e.forceLongPolling, this.autoDetectLongPolling = e.autoDetectLongPolling, this.useFetchStreams = e.useFetchStreams, this.longPollingOptions = e.longPollingOptions;
    console.log('test');
  }
}
""";
    String expectedCode =
        """
const testcode$classextends$var0 = class __PRIVATE_RestConnection {
  constructor(e) {
    this.databaseInfo = e, this.databaseId = e.databaseId;
  }
};
class __PRIVATE_WebChannelConnection extends testcode$classextends$var0 {
  constructor(e) {
    super(e), this.forceLongPolling = e.forceLongPolling, this.autoDetectLongPolling = e.autoDetectLongPolling, this.useFetchStreams = e.useFetchStreams, this.longPollingOptions = e.longPollingOptions;
    console.log('test');
  }
}
""";
    test(code, expectedCode);
  }

  @Test
  public void testVarDeclaration() {
    test(
        """
        const foo = {'bar': Object};
        /** @extends {Object} */ var Foo = class extends foo['bar'] {};
        """,
        """
        const foo = {'bar': Object};
        const testcode$classextends$var0 = foo['bar'];
        var Foo = class extends testcode$classextends$var0 {};
        """);

    test(
        """
        const foo = {'bar': Object};
        /** @extends {Object} */ let Foo = class extends foo['bar'] {};
        """,
        """
        const foo = {'bar': Object};
        const testcode$classextends$var0 = foo['bar'];
        let Foo = class extends testcode$classextends$var0 {};
        """);

    test(
        """
        const foo = {'bar': Object};
        /** @extends {Object} */ const Foo = class extends foo['bar'] {};
        """,
        """
        const foo = {'bar': Object};
        const testcode$classextends$var0 = foo['bar'];
        const Foo = class extends testcode$classextends$var0 {};
        """);
  }

  @Test
  public void testDeclarationInForLoop() {
    test(
        """
        const foo = {'bar': Object};
        for (let Foo = /** @extends {Object} */ class extends foo['bar'] {}, i = 0;
            i < 1;
            i++)
          {}
        """,
        """
        const foo = {'bar': Object};
        // use an iife since parent of the let isn't a block where we can add statements
        // TODO(bradfordcsmith): Would it be better (smaller output code) to just declare
        //     the temporary variable in the block containing the for-loop?
        for (let Foo = (function() {
            const testcode$classextends$var0 = foo['bar'];
            return class extends testcode$classextends$var0 {};
          })(), i = 0; i < 1; i++) {}
        """);
  }

  @Test
  public void testAssign() {
    test(
        """
        const foo = {'bar': Object};
        var Foo;
        /** @extends {Object} */
        Foo = class extends foo['bar'] {};
        """,
        """
        const foo = {'bar': Object};
        var Foo;
        const testcode$classextends$var0 = foo['bar'];
        Foo = class extends testcode$classextends$var0 {};
        """);

    test(
        """
        const foo = {'bar': Object};
        /** @extends {Object} */
        foo.baz = class extends foo['bar'] {};
        """,
        """
        const foo = {'bar': Object};
        const testcode$classextends$var0 = foo['bar'];
        foo.baz = class extends testcode$classextends$var0 {};
        """);

    test(
        """
        const foo = {'bar': Object};
        /** @extends {Object} */
        foo.foo = foo.baz = class extends foo['bar'] {};
        """,
        """
        const foo = {'bar': Object};
        foo.foo = foo.baz = (function() {
          const testcode$classextends$var0 = foo['bar'];
          return class extends testcode$classextends$var0 {};
        })();
        """);

    test(
        """
        const foo = {'bar': Object};
        /** @extends {Object} */
        foo['baz'] = class extends foo['bar'] {};
        """,
        """
        const foo = {'bar': Object};
        const testcode$classextends$var0 = foo['bar'];
        foo['baz'] = class extends testcode$classextends$var0 {};
        """);

    test(
        """
        const foo = {'bar': Object, baz: {}};
        /** @extends {Object} */
        foo.baz['foo'] = class extends foo['bar'] {};
        """,
        """
        const foo = {'bar': Object, baz: {}};
        const testcode$classextends$var0 = foo['bar'];
        foo.baz['foo'] = class extends testcode$classextends$var0 {};
        """);
  }

  @Test
  public void testAssignWithSideEffects() {
    test(
        """
        let baz = 1;
        const foo = {'bar': Object};
        /** @extends {Object} */
        foo[baz++] = class extends foo['bar'] {};
        """,
        """
        let baz = 1;
        const foo = {'bar': Object};
        // Use an IIFE to preserve execution order when expressions have side effects.
        foo[baz++] = (function() {
          const testcode$classextends$var0 = foo['bar'];
          return class extends testcode$classextends$var0 {};
        })();
        """);
  }

  @Test
  public void testMultipleVarDeclaration() {
    test(
        """
        const foo = { 'bar': Object};
        function mayHaveSideEffects() {}
        var baz = mayHaveSideEffects(),
            Foo = /** @extends {Object} */ class extends foo['bar'] {};
        """,
        """
        const foo = { 'bar': Object};
        function mayHaveSideEffects() {}
        var baz = mayHaveSideEffects(), Foo = (function() {
          const testcode$classextends$var0 = foo['bar'];
          return class extends testcode$classextends$var0 {};
        })();
        """);

    test(
        """
        const foo = {'bar': Object};
        const Foo = /** @extends {Object} */ class extends foo['bar'] {}, baz = false;
        """,
        """
        const foo = {'bar': Object};
        const testcode$classextends$var0 = foo['bar'];
        const Foo = class extends testcode$classextends$var0 {}, baz = false;
        """);
  }
}
