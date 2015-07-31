package de.halirutan.mathematica;

import com.intellij.lang.FileASTNode;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.ResourceUtil;
import de.halirutan.mathematica.filetypes.MathematicaFileType;
import de.halirutan.mathematica.parsing.psi.api.MathematicaPsiFile;
import de.halirutan.mathematica.parsing.psi.util.FullFormCreator;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * @author patrick (25.07.15)
 */

public class Main {

  public static void main(String[] args) throws IOException {
    PsiFileFactory psiFileFactory = createPsiFactory();
    final URL fileResource = ResourceUtil.getResource(
        Main.class,
        "de/halirutan/mathematica",
        "Test.m");
    File file = new File(fileResource.getFile());
    String source = FileUtil.loadFile(file);
    FileASTNode root = parseSource(source, psiFileFactory);
    if (root.getPsi() instanceof MathematicaPsiFile) {
      final String fullForm = FullFormCreator.createFullForm(root.getPsi(MathematicaPsiFile.class));
      System.out.println(fullForm);
    }
  }

  private static PsiFileFactory createPsiFactory() {
    MockProject mockProject = createProject();
    return PsiFileFactory.getInstance(mockProject);
  }

  private static FileASTNode parseSource(String source, PsiFileFactory psiFileFactory) {
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
