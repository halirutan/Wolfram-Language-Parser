package de.halirutan.mathematica;

import com.intellij.lang.FileASTNode;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ResourceUtil;
import de.halirutan.mathematica.filetypes.MathematicaFileType;
import de.halirutan.mathematica.parsing.MathematicaElementTypes;
import de.halirutan.mathematica.parsing.psi.api.MathematicaPsiFile;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * @author patrick (25.07.15)
 */

public class Main {

  private static final TokenSet atoms = TokenSet.create(
      MathematicaElementTypes.STRING_LITERAL_EXPRESSION,
      MathematicaElementTypes.NUMBER_EXPRESSION,
      MathematicaElementTypes.SYMBOL_EXPRESSION,
      MathematicaElementTypes.STRINGIFIED_SYMBOL_EXPRESSION

  );

  public static void main(String[] args) throws IOException {
    PsiFileFactory psiFileFactory = createPsiFactory();
    final URL source = ResourceUtil.getResource(
        Main.class,
        "de/halirutan/mathematica",
        "spacingExample.m");
    File file = new File(source.getFile());
    String javaSource = FileUtil.loadFile(file);
    FileASTNode root = parseJavaSource(javaSource, psiFileFactory);

    final PsiElement[] children = root.getPsi().getChildren();
    for (PsiElement child : children) {
      if(child instanceof PsiWhiteSpace) continue;
      printFullForm(child);
      System.out.println("\n");
    }

  }

  private static void printFullForm(PsiElement psiElement) {

    if (psiElement != null) {
      final IElementType type = psiElement.getNode().getElementType();
      if (atoms.contains(type)) {
        System.out.print(psiElement.getText());
        return;
      }

      if (psiElement instanceof PsiWhiteSpace) {
        return;
      }

      final PsiElement[] children = psiElement.getChildren();
      if (type == MathematicaElementTypes.FUNCTION_CALL_EXPRESSION) {
        System.out.print(psiElement.getFirstChild().getText() + "[");
        for (int i = 1; i < children.length; i++) {
          PsiElement child = children[i];
          printFullForm(child);
        }
        System.out.print("]");
      } else {
        System.out.print(psiElement.toString() + "[");
        boolean first = true;
        for (PsiElement child : children) {
          if (first) {
            first = false;
          } else if(!(child instanceof PsiWhiteSpace)) {
            System.out.print(",");
          }
          printFullForm(child);

        }
        System.out.print("]");

      }
    }
  }

  private static PsiFileFactory createPsiFactory() {
    MockProject mockProject = createProject();
    return PsiFileFactory.getInstance(mockProject);
  }

  private static FileASTNode parseJavaSource(String source, PsiFileFactory psiFileFactory) {
    PsiFile psiFile = psiFileFactory.createFileFromText("__dummy_file__.m", MathematicaFileType.INSTANCE, source);
    MathematicaPsiFile psiFile1 = (MathematicaPsiFile) psiFile;
    return psiFile1.getNode();
  }

  private static MockProject createProject() {
    MathematicaCoreProjectEnvironment environment = new MathematicaCoreProjectEnvironment(new Disposable() {
      @Override
      public void dispose() {
      }
    }, new MathematicaCoreApplicationEnvironment(new Disposable() {
      @Override
      public void dispose() {
      }
    }));

    return environment.getProject();
  }


}
