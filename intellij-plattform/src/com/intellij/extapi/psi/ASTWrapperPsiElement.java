//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.intellij.extapi.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import org.jetbrains.annotations.NotNull;

public class ASTWrapperPsiElement extends ASTDelegatePsiElement {
  private final ASTNode myNode;

  public ASTWrapperPsiElement(@NotNull ASTNode node) {
    this.myNode = node;
  }

  public PsiElement getParent() {
    return SharedImplUtil.getParent(this.getNode());
  }

  @NotNull
  public ASTNode getNode() {
    ASTNode var10000 = this.myNode;
    if(this.myNode == null) {
      throw new IllegalStateException(String.format("@NotNull method %s.%s must not return null", new Object[]{"com/intellij/extapi/psi/ASTWrapperPsiElement", "getNode"}));
    } else {
      return var10000;
    }
  }

  public String toString() {
    return this.getClass().getSimpleName() + "(" + this.myNode.getElementType().toString() + ")";
  }
}