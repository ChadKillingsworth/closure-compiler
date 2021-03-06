/*
 * Copyright 2009 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;

/**
 * An AST generated totally by the compiler.
 */
public final class SyntheticAst implements SourceAst {

  private final InputId inputId;
  private final SourceFile sourceFile;

  private Node root;

  @VisibleForTesting
  public static SyntheticAst emptyWithFileName(String sourceName, SourceKind kind) {
    return new SyntheticAst(sourceName, kind);
  }

  SyntheticAst(String sourceName) {
    this(sourceName, SourceKind.STRONG);
  }

  SyntheticAst(InputId inputId) {
    this(inputId, SourceKind.STRONG);
  }

  SyntheticAst(String sourceName, SourceKind kind) {
    this(new InputId(sourceName), kind);
  }

  SyntheticAst(InputId inputId, SourceKind kind) {
    this.inputId = inputId;
    this.sourceFile = SourceFile.fromCode(inputId.getIdName(), "", kind);
    clearAst();
  }

  public SyntheticAst(Node root) {
    this.inputId = new InputId(root.getSourceFileName());
    this.sourceFile = SourceFile.fromCode(root.getSourceFileName(), "", SourceKind.STRONG);
    this.root = checkNotNull(root);
  }

  @Override
  public Node getAstRoot(AbstractCompiler compiler) {
    return root;
  }

  @Override
  public void clearAst() {
    root = IR.script();
    root.setInputId(inputId);
    root.setStaticSourceFile(sourceFile);
  }

  @Override
  public InputId getInputId() {
    return inputId;
  }

  @Override
  public SourceFile getSourceFile() {
    return sourceFile;
  }

  @Override
  public void setSourceFile(SourceFile file) {
    throw new IllegalStateException(
        "Cannot set a source file for a synthetic AST");
  }
}
