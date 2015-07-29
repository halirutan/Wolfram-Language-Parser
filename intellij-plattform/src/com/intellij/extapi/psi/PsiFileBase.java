//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.intellij.extapi.psi;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.tree.IFileElementType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public abstract class PsiFileBase extends PsiFileImpl {
  @NotNull
  private final Language myLanguage;
  @NotNull
  private final ParserDefinition myParserDefinition;

  protected PsiFileBase(@NotNull FileViewProvider viewProvider, @NotNull Language language) {
    super(viewProvider);
    this.myLanguage = findLanguage(language, viewProvider);
    ParserDefinition parserDefinition = (ParserDefinition)LanguageParserDefinitions.INSTANCE.forLanguage(this.myLanguage);
    if(parserDefinition == null) {
      throw new RuntimeException("PsiFileBase: language.getParserDefinition() returned null for: " + this.myLanguage);
    } else {
      this.myParserDefinition = parserDefinition;
      IFileElementType nodeType = parserDefinition.getFileNodeType();

      assert nodeType.getLanguage() == this.myLanguage : nodeType.getLanguage() + " != " + this.myLanguage;

      this.init(nodeType, nodeType);
    }
  }

  private static Language findLanguage(Language baseLanguage, FileViewProvider viewProvider) {
    Set languages = viewProvider.getLanguages();
    Iterator i$ = languages.iterator();

    Language actualLanguage;
    do {
      if(!i$.hasNext()) {
        throw new AssertionError("Language " + baseLanguage + " doesn\'t participate in view provider " + viewProvider + ": " + new ArrayList(languages));
      }

      actualLanguage = (Language)i$.next();
    } while(!actualLanguage.isKindOf(baseLanguage));

    return actualLanguage;
  }

  @NotNull
  public final Language getLanguage() {
    Language var10000 = this.myLanguage;
    if(this.myLanguage == null) {
      throw new IllegalStateException(String.format("@NotNull method %s.%s must not return null", new Object[]{"com/intellij/extapi/psi/PsiFileBase", "getLanguage"}));
    } else {
      return var10000;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitFile(this);
  }

  @NotNull
  public ParserDefinition getParserDefinition() {
    ParserDefinition var10000 = this.myParserDefinition;
    if(this.myParserDefinition == null) {
      throw new IllegalStateException(String.format("@NotNull method %s.%s must not return null", new Object[]{"com/intellij/extapi/psi/PsiFileBase", "getParserDefinition"}));
    } else {
      return var10000;
    }
  }
}
