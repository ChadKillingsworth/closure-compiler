/*
 * Copyright 2013 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.deps.ModuleLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for type-checking across commonjs modules.
 *
 * @author nicholas.j.santos@gmail.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public final class ModuleRewriteIntegrationTest extends IntegrationTestCase {
  @Test
  public void testCrossModuleCtorCall() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          "var Hello = require('./i0'); var hello = new Hello();"
        },
        new String[] {
          "/** @const */ var module$i0 = {}; /** @const */ module$i0.default = function (){};",
          LINE_JOINER.join("var Hello = module$i0.default;", "var hello = new module$i0.default();")
        });
  }

  @Test
  public void testCrossModuleCtorCall2() {
    test(createCompilerOptions(),
         new String[] {
           "/** @constructor */ function Hello() {} " +
           "module.exports = Hello;",

           "var Hello = require('./i0');" +
           "var hello = new Hello(1);"
         },
         TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  @Test
  public void testCrossModuleTypeAnnotation() {
    test(
        createCompilerOptions(),
        LINE_JOINER.join(
            "/** @constructor */ function Hello() {} ",
            "/** @type {!Hello} */ var hello = new Hello();",
            "module.exports = Hello;"),
        LINE_JOINER.join(
            "/** @const */ var module$i0 = {};",
            "module$i0.default = function () {};",
            "var hello$$module$i0 = new module$i0.default();"));
  }

  @Test
  public void testCrossModuleTypeAnnotation2() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          "var Hello = require('./i0'); /** @type {!Hello} */ var hello = new Hello();"
        },
        new String[] {
          LINE_JOINER.join(
          "/** @const */ var module$i0 = {};",
          "/** @const */ module$i0.default = /** @constructor */ function() {};"),
          "var Hello = module$i0.default; var hello = new module$i0.default();"
        });
  }

  @Test
  public void testCrossModuleTypeAnnotation3() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          "var Hello = require('./i0'); /** @type {!Hello} */ var hello = 1;"
        },
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  @Test
  public void testCrossModuleSubclass1() {
    test(
        createCompilerOptions(),
        new String[] {
          LINE_JOINER.join("/** @constructor */ function Hello() {}", "module.exports = Hello;"),
          LINE_JOINER.join(
              "var Hello = require('./i0');",
              "var util = {inherits: function (x, y){}};",
              "/**\n",
              " * @constructor\n",
              " * @extends {Hello}\n",
              " */\n",
              "var SubHello = function () {};",
              "util.inherits(SubHello, Hello);")
        },
        new String[] {
          "/** @const */ var module$i0 = {}; /** @const */ module$i0.default = function (){};",
          LINE_JOINER.join(
              "var Hello = module$i0.default;",
              "var util = { inherits : function(x,y) {} };",
              "var SubHello = function() {};",
              "util.inherits(SubHello, module$i0.default);")
        });
  }

  @Test
  public void testCrossModuleSubclass2() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          LINE_JOINER.join(
              "var Hello = require('./i0');",
              "var util = {inherits: function (x, y){}};",
              "/**",
              " * @constructor",
              " * @extends {Hello}",
              " */",
              "function SubHello() {}",
              "util.inherits(SubHello, Hello);")
        },
        new String[] {
          "/** @const */ var module$i0 = {}; module$i0.default = function (){};",
          LINE_JOINER.join(
              "var Hello = module$i0.default;",
              "var util = { inherits : function(x,y) {} };",
              "function SubHello(){}",
              "util.inherits(SubHello, module$i0.default);")
        });
  }

  @Test
  public void testCrossModuleSubclass3() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {}  module.exports = Hello;",
          LINE_JOINER.join(
              "var Hello = require('./i0');",
              "var util = {inherits: function (x, y){}};",
              "/**",
              " * @constructor",
              " * @extends {Hello}",
              " */",
              "function SubHello() { Hello.call(this); }",
              "util.inherits(SubHello, Hello);")
        },
        new String[] {
          LINE_JOINER.join(
              "/** @const */ var module$i0 = {};",
              "/** @constructor */ module$i0.default = function (){};"),
          LINE_JOINER.join(
              "var Hello = module$i0.default;",
              "var util = { inherits : function(x,y) {} };",
              "function SubHello(){ module$i0.default.call(this); }",
              "util.inherits(SubHello, module$i0.default);")
        });
  }

  @Test
  public void testCrossModuleSubclass4() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {}  module.exports = {Hello: Hello};",
          LINE_JOINER.join(
              "var i0 = require('./i0');",
              "var util = {inherits: function (x, y) {}};",
              "/**",
              " * @constructor",
              " * @extends {i0.Hello}",
              " */",
              "function SubHello() { i0.Hello.call(this); }",
              "util.inherits(SubHello, i0.Hello);")
        },
        new String[] {
          LINE_JOINER.join(
              "/** @const */ var module$i0 = { /** @const */ default: {}};",
              "module$i0.default.Hello = /** @constructor */ function (){};"),
          LINE_JOINER.join(
              "var i0 = module$i0.default;",
              "var util = { inherits : function(x,y) {} };",
              "function SubHello(){ module$i0.default.Hello.call(this); }",
              "util.inherits(SubHello, module$i0.default.Hello);")
        });
  }

  @Test
  public void testCrossModuleSubclass5() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          LINE_JOINER.join(
              "var Hello = require('./i0');",
              "var util = {inherits: function (x, y){}};",
              "/**",
              " * @constructor",
              " * @extends {./i0}",
              " */",
              "function SubHello() { Hello.call(this); }",
              "util.inherits(SubHello, Hello);")
        },
        new String[] {
          LINE_JOINER.join(
              "/** @const */ var module$i0 = {};",
              "/** @constructor */ module$i0.default = function (){};"),
          LINE_JOINER.join(
              "var Hello = module$i0.default;",
              "var util = { inherits : function(x,y) {} };",
              "function SubHello(){ module$i0.default.call(this); }",
              "util.inherits(SubHello, module$i0.default);")
        });
  }

  @Test
  public void testCrossModuleSubclass6() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = {Hello: Hello};",
          LINE_JOINER.join(
              "var i0 = require('./i0');",
              "var util = {inherits: function (x, y){}};",
              "/**",
              " * @constructor",
              " * @extends {./i0.Hello}",
              " */",
              "function SubHello() { i0.Hello.call(this); }",
              "util.inherits(SubHello, i0.Hello);")
        },
        new String[] {
          LINE_JOINER.join(
              "/** @const */ var module$i0 = { /** @const */ default: {}};",
              "module$i0.default.Hello = /** @constructor */ function (){};"),
          LINE_JOINER.join(
              "var i0 = module$i0.default;",
              "var util = {inherits:function(x,y){}};",
              "function SubHello(){ module$i0.default.Hello.call(this); }",
              "util.inherits(SubHello, module$i0.default.Hello);")
        });
  }

  @Test
  public void testCommonJSImportsESModule() {
    test(
        createCompilerOptions(),
        new String[] {
          LINE_JOINER.join(
              "/** @constructor */ export default function Hello() {};", "export const foo = 1;"),
          LINE_JOINER.join(
              "var i0 = require('./i0');",
              "var util = {inherits: function (x, y){}};",
              "/**",
              " * @constructor",
              " * @extends {./i0.default}",
              " */",
              "function SubHello() { i0.default.call(this); }",
              "util.inherits(SubHello, i0.default);",
              "const {foo} = i0;")
        },
        new String[] {
          LINE_JOINER.join(
              "function Hello$$module$i0() {}",
              "var foo$$module$i0 = 1;",
              "var module$i0 = {};",
              "module$i0.default = Hello$$module$i0;",
              "module$i0.foo = foo$$module$i0;"),
          LINE_JOINER.join(
              "var i0 = module$i0;",
              "var util = {inherits:function(x,y){}};",
              "function SubHello(){ module$i0.default.call(this); }",
              "util.inherits(SubHello, module$i0.default);",
              "var $jscomp$destructuring$var0 = module$i0;",
              "var foo = $jscomp$destructuring$var0.foo;")
        });
  }

  @Test
  public void testESModuleImportsCommonJS1() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          LINE_JOINER.join(
              "import Hello from './i0';",
              "var util = {inherits: function (x, y){}};",
              "/**",
              " * @constructor",
              " * @extends {Hello}",
              " */",
              "function SubHello() { Hello.call(this); }",
              "util.inherits(SubHello, Hello);")
        },
        new String[] {
          LINE_JOINER.join(
              "/** @const */ var module$i0 = {};",
              "/** @const */ module$i0.default = /** @constructor */ function (){};"),
          LINE_JOINER.join(
              "var util$$module$i1 = {inherits:function(x,y){}};",
              "/**",
              " * @constructor",
              " * @extends {module$i0.default}",
              " */",
              "function SubHello$$module$i1(){ module$i0.default.call(this); }",
              "util$$module$i1.inherits(SubHello, module$i0.default);",
              "var module$i1 = {};")
        });
  }

  @Test
  public void testESModuleImportsCommonJS2() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          LINE_JOINER.join(
              "import * as i0 from './i0';",
              "var util = {inherits: function (x, y){}};",
              "/**",
              " * @constructor",
              " * @extends {i0.default}",
              " */",
              "function SubHello() { i0.default.call(this); }",
              "util.inherits(SubHello, i0.default);")
        },
        new String[] {
          LINE_JOINER.join(
              "/** @const */ var module$i0 = {};",
              "/** @const */ module$i0.default = /** @constructor */ function (){};"),
          LINE_JOINER.join(
              "var util$$module$i1 = {inherits:function(x,y){}};",
              "function SubHello$$module$i1(){ module$i0.default.call(this); }",
              "util$$module$i1.inherits(SubHello$$module$i1, module$i0.default);",
              "var module$i1 = {};")
        });
  }

  @Test
  public void testESModuleImportsCommonJS3() {
    test(
        createCompilerOptions(),
        new String[] {
          "module.exports = {foo: 1, bar: function() { return 'bar'; }};",
          LINE_JOINER.join("import i0 from './i0';", "const {foo} = i0;", "const b = i0.bar();")
        },
        new String[] {
          LINE_JOINER.join(
              "/** @const */ var module$i0 = {/** @const */ default: {}};",
              "module$i0.default.foo = 1;",
              "module$i0.default.bar = function() { return 'bar'; };"),
          LINE_JOINER.join(
              "var $jscomp$destructuring$var0 = module$i0.default;",
              "var foo$$module$i1 = $jscomp$destructuring$var0.foo;",
              "var b$$module$i1 = module$i0.bar();",
              "var module$i1 = {};")
        });
  }

  @Override
  protected CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setCodingConvention(new GoogleCodingConvention());
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setProcessCommonJSModules(true);
    options.setClosurePass(true);
    options.setModuleResolutionMode(ModuleLoader.ResolutionMode.NODE);
    return options;
  }
}
