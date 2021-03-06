/*
 * Copyright 2012-2014 Sergey Ignatov
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

package org.intellij.erlang.rebar.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.erlang.psi.*;
import org.intellij.erlang.utils.ErlangTermFileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class RebarConfigUtil {
  private RebarConfigUtil() {
  }

  @NotNull
  public static List<String> getIncludePaths(@NotNull ErlangFile rebarConfig) {
    final List<String> includePaths = new ArrayList<>();
    ErlangTermFileUtil.processConfigSection(rebarConfig, "erl_opts", section -> ErlangTermFileUtil.processConfigSection(section, "i", includeOptionValue -> {
      if (includeOptionValue instanceof ErlangStringLiteral) {
        includePaths.add(getStringLiteralText((ErlangStringLiteral) includeOptionValue));
      }
      else {
        for (ErlangStringLiteral includePath : PsiTreeUtil.findChildrenOfType(includeOptionValue, ErlangStringLiteral.class)) {
          includePaths.add(getStringLiteralText(includePath));
        }
      }
    }));
    return includePaths;
  }

  @NotNull
  public static List<String> getExtraSrcDirs(@NotNull ErlangFile rebarConfig) {
    final List<String> srcDirs = new ArrayList<>();
    ErlangTermFileUtil.processConfigSection(rebarConfig, "extra_src_dirs", srcDirList -> {
      if (srcDirList instanceof ErlangListExpression) {
        List<ErlangExpression> expressionList = ((ErlangListExpression) srcDirList).getExpressionList();
        expressionList.forEach(erlangExpression -> {
          String path = erlangExpression.getText();
          if (path.length() > 2) {
            path = path.substring(1, path.length() - 1);
            srcDirs.add(path);
          }
        });
      }
    });
    return srcDirs;
  }


  @NotNull
  public static List<String> getDependencyAppNames(@NotNull ErlangFile rebarConfig) {
    final List<String> dependencyAppNames = new ArrayList<>();
    ErlangTermFileUtil.processConfigSection(rebarConfig, "deps", tuplesList -> {
      List<ErlangTupleExpression> dependencyTuples = ErlangTermFileUtil.findNamedTuples(tuplesList);
      for (ErlangTupleExpression namedTuple : dependencyTuples) {
        dependencyAppNames.add(ErlangTermFileUtil.getNameOfNamedTuple(namedTuple));
      }
    });
    return dependencyAppNames;
  }

  @NotNull
  public static List<String> getParseTransforms(@Nullable ErlangFile rebarConfig) {
    final List<String> parseTransforms = new ArrayList<>();
    ErlangTermFileUtil.processConfigSection(rebarConfig, "erl_opts", section -> ErlangTermFileUtil.processConfigSection(section, "parse_transform", configExpression -> {
      ErlangQAtom parseTransform = PsiTreeUtil.getChildOfType(configExpression, ErlangQAtom.class);
      ErlangAtom parseTransformAtom = parseTransform != null ? parseTransform.getAtom() : null;
      if (parseTransformAtom != null) {
        parseTransforms.add(parseTransformAtom.getName());
      }
    }));
    return parseTransforms;
  }

  @NotNull
  private static String getStringLiteralText(@NotNull ErlangStringLiteral literal) {
    return StringUtil.unquoteString(literal.getString().getText());
  }

  @Nullable
  public static ErlangFile getRebarConfig(@NotNull Project project, @Nullable VirtualFile otpAppRoot) {
    VirtualFile rebarConfig = otpAppRoot != null ? otpAppRoot.findChild("rebar.config") : null;
    PsiFile rebarConfigPsi = rebarConfig != null && !rebarConfig.isDirectory() ? PsiManager.getInstance(project).findFile(rebarConfig) : null;
    return rebarConfigPsi instanceof ErlangFile ? (ErlangFile) rebarConfigPsi : null;
  }

  public static void calcApps(VirtualFile appDir, Set<String> apps) {
    VfsUtilCore.visitChildrenRecursively(appDir, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (file.isDirectory()) {
          VirtualFile appResourceFile = findAppResourceFile(file);
          if (appResourceFile != null) {
            String appName = StringUtil.trimEnd(StringUtil.trimEnd(appResourceFile.getName(), ".src"), ".app");
            apps.add(appName);
          }
          return true;
        }
        return true;
      }
    });
  }

  @Nullable
  public static VirtualFile findAppResourceFile(@NotNull VirtualFile applicationRoot) {
    VirtualFile appResourceFile = null;
    VirtualFile sourceDir = applicationRoot.findChild("src");
    if (sourceDir != null) {
      appResourceFile = findFileByExtension(sourceDir, "app.src");
    }
    if (appResourceFile == null) {
      VirtualFile ebinDir = applicationRoot.findChild("ebin");
      if (ebinDir != null) {
        appResourceFile = findFileByExtension(ebinDir, "app");
      }
    }
    return appResourceFile;
  }

  @Nullable
  private static VirtualFile findFileByExtension(@NotNull VirtualFile dir, @NotNull String extension) {
    for (VirtualFile file : dir.getChildren()) {
      if (!file.isDirectory() && file.getName().endsWith(extension)) return file;
    }
    return null;
  }
}
