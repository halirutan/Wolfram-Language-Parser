package de.halirutan.mathematica.parsing.psi.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import de.halirutan.mathematica.parsing.MathematicaElementTypes;
import de.halirutan.mathematica.parsing.psi.MathematicaVisitor;
import de.halirutan.mathematica.parsing.psi.api.*;
import de.halirutan.mathematica.parsing.psi.api.Number;
import de.halirutan.mathematica.parsing.psi.api.function.Function;
import de.halirutan.mathematica.parsing.psi.api.string.MString;

/**
 * @author patrick (30.07.15)
 */
public class FullFormCreator {


  private static StringBuilder myFullFormString;

  public static String createFullForm(MathematicaPsiFile file) {
    myFullFormString = new StringBuilder(file.getTextLength());
    FullFormVisitor visitor = new FullFormVisitor();
    file.accept(visitor);
    return myFullFormString.toString();
  }

  static class FullFormVisitor extends MathematicaVisitor {

    @Override
    public void visitFile(final PsiFile file) {
      file.acceptChildren(this);
    }

    @Override
    public void visitElement(final PsiElement element) {
      super.visitElement(element);
      final String head = element.toString();
      final PsiElement[] children = element.getChildren();
      myFullFormString.append(head).append("[");
      for (int i = 0; i < children.length; i++) {
        PsiElement child = children[i];
        child.accept(this);
        if (i != children.length-1 && !(child instanceof PsiWhiteSpace) && child.getNextSibling() != null) {
          myFullFormString.append(",");
        }
      }
      myFullFormString.append("]");
    }

    @Override
    public void visitFunctionCall(final FunctionCall functionCall) {
      functionCall.getHead().accept(this);
      myFullFormString.append("[");
      final PsiElement[] children = functionCall.getChildren();
      for (int num = 1; num < children.length; num++) {
        PsiElement child = children[num];
        child.accept(this);
        if (num != children.length - 1 && !(child instanceof PsiWhiteSpace)) {
          myFullFormString.append(",");
        }
      }
      myFullFormString.append("]");
    }

    @Override
    public void visitSymbol(final Symbol symbol) {
      myFullFormString.append(symbol.getSymbolName());
    }

    @Override
    public void visitString(final MString string) {
      myFullFormString.append(string.getText());
    }

    @Override
    public void visitStringifiedSymbol(final StringifiedSymbol stringifiedSymbol) {
      myFullFormString.append('"').append(stringifiedSymbol.getText()).append('"');
    }

    @Override
    public void visitSlot(final Slot slot) {
      final IElementType elementType = slot.getNode().getElementType();
      if (elementType == MathematicaElementTypes.SLOT) {
        String num = slot.getText().substring(1);
        num = num.length() > 0 ? num : "1";
        myFullFormString.append("Slot[").append(num).append("]");
      }
      if (elementType == MathematicaElementTypes.SLOT_SEQUENCE) {
        String num = slot.getText().substring(2);
        num = num.length() > 0 ? num : "1";
        myFullFormString.append("SlotSequence[").append(num).append("]");
      }



    }

    @Override
    public void visitFunction(final Function function) {
      myFullFormString.append("Function[");
      function.getFirstChild().accept(this);
      myFullFormString.append(']');
    }

    @Override
    public void visitNumber(final Number number) {
      myFullFormString.append(number.getText());
    }
  }


}
