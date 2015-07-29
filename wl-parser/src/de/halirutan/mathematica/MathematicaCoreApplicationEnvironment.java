/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.halirutan.mathematica;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreJavaDirectoryService;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.ClassExtension;
import com.intellij.openapi.util.Disposer;
import de.halirutan.mathematica.filetypes.MathematicaFileType;
import de.halirutan.mathematica.parsing.prattparser.MathematicaParserDefinition;

/**
 * @author yole
 */
public class MathematicaCoreApplicationEnvironment extends CoreApplicationEnvironment {
  public MathematicaCoreApplicationEnvironment(Disposable parentDisposable) {
    super(parentDisposable);

    registerFileType(MathematicaFileType.INSTANCE, "m");
    addExplicitExtension(LanguageParserDefinitions.INSTANCE, MathematicaLanguage.INSTANCE, new MathematicaParserDefinition());
  }
}
