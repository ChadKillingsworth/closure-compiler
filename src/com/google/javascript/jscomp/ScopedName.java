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

import com.google.javascript.rhino.Node;
import org.jspecify.annotations.Nullable;

/**
 * A name together with a scope root node.  This is suitable for use as a
 * map key and is the base class for Var.  Note that {@link #equals} and
 * {@link #hashCode} as defined at this level, even though subclasses may
 * add additional mutable state.  The idea is that if two var objects have
 * the same name and scope root, then they really are "the same var" and
 * should be treated as interchangeable.  This does mean that we need to be
 * careful to not depend on any other details in situations where vars are
 * stored in sets.
 */
abstract class ScopedName {

  /** Returns the name as a string. */
  abstract String getName();

  /** Returns the root node of the scope in which this name is defined. */
  abstract Node getScopeRoot();

  static ScopedName of(String name, @Nullable Node scopeRoot) {
    return new Simple(name, scopeRoot);
  }

  /** Simple implementation with no additional data or semantics. */
  private static class Simple extends ScopedName {
    final String name;
    final Node scopeRoot;

    Simple(String name, Node scopeRoot) {
      this.name = name;
      this.scopeRoot = scopeRoot;
    }

    @Override
    String getName() {
      return name;
    }

    @Override
    Node getScopeRoot() {
      return scopeRoot;
    }
  }

  @Override
  public final boolean equals(Object other) {
    if (!(other instanceof ScopedName scopedName)) {
      return false;
    }
    return getName().equals(scopedName.getName())
        && getScopeRoot().equals(scopedName.getScopeRoot());
  }

  @Override
  public final int hashCode() {
    // NOTE: here we avoid Object.hash because of this function is called
    // frequently enough that the overhead of implicit array creation of
    // a varargs function is enough that we want to avoid it.
    String name = getName();
    Node root = getScopeRoot();
    int result = 1;
    result = 31 * result + name.hashCode();
    result = 31 * result + root.hashCode();
    return result;
  }
}
