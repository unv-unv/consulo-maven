/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.documentation.LanguageDocumentationProvider;
import consulo.language.psi.PsiElement;
import consulo.xml.lang.xml.XMLLanguage;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class MavenPluginModelDocumentationProvider implements LanguageDocumentationProvider {
    @Override
    public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return getDocForMavenPluginParameter(element, false);
    }

    @Override
    public String generateDoc(PsiElement element, PsiElement originalElement) {
        return getDocForMavenPluginParameter(element, true);
    }

    private String getDocForMavenPluginParameter(PsiElement element, boolean html) {
        MavenPluginConfigurationDomExtender.ParameterData p = element.getUserData(MavenPluginConfigurationDomExtender.PLUGIN_PARAMETER_KEY);
        if (p == null) {
            return null;
        }

        String[] ss = html
            ? new String[]{"<br>", "<b>", "</b>", "<i>", "</i>"}
            : new String[]{"\n ", "", "", "", ""};

        String text = "";
        if (html) {
            text += "Type: " + ss[1] + p.parameter.getType().getStringValue() + ss[2] + ss[0];
            if (p.defaultValue != null) {
                text += "Default Value: " + ss[1] + p.defaultValue + ss[2] + ss[0];
            }
            if (p.expression != null) {
                text += "Expression: " + ss[1] + p.expression + ss[2] + ss[0];
            }
            if (p.parameter.getRequired().getValue() == Boolean.TRUE) {
                text += ss[1] + "Required" + ss[2] + ss[0];
            }
            text += ss[0];
        }
        text += ss[3] + p.parameter.getDescription().getStringValue() + ss[4];
        return text;
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return XMLLanguage.INSTANCE;
    }
}
