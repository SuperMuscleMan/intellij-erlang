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

package org.intellij.erlang.rebar.importWizard;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.erlang.psi.*;
import org.intellij.erlang.utils.ErlangTermFileUtil;
import org.intellij.erlang.rebar.util.RebarConfigUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ImportedOtpApp {
  private String myName;
  private final VirtualFile myRoot;
  private final Set<String> myDeps = new HashSet<>();
  private final Set<VirtualFile> myIncludePaths = new HashSet<>();
  private final Set<VirtualFile> mySourcePaths = new HashSet<>();
  private final Set<VirtualFile> myTestPaths = new HashSet<>();
  private final Set<String> myApps = new HashSet<>();
  private final Set<String> myParseTransforms = new HashSet<>();
  private final Boolean myIsRebar3;
  @Nullable
  private VirtualFile myIdeaModuleFile;
  private Module myModule;
  private String myAppDirPath;


  public ImportedOtpApp(@NotNull VirtualFile root, @NotNull final VirtualFile appConfig, Boolean isRebar3) {
    myRoot = root;
    myName = StringUtil.trimEnd(StringUtil.trimEnd(appConfig.getName(), ".src"), ".app");
    ApplicationManager.getApplication().runReadAction(() -> {
      addDependenciesFromAppFile(appConfig);
      addInfoFromRebarConfig();
      addPath(myRoot, "src", mySourcePaths);
      addPath(myRoot, "test", myTestPaths);
      addPath(myRoot, "include", myIncludePaths);
    });
    myIsRebar3 = isRebar3;
  }


  public ImportedOtpApp(@NotNull VirtualFile root, Boolean isRebar3) {
    myRoot = root;
    VirtualFile appDir = isRebar3 ? root.findChild("apps") : root;
    myIsRebar3 = isRebar3;

    ApplicationManager.getApplication().runReadAction(() -> {
      addInfoFromRebarConfig();
      if (isRebar3) {
        myName = root.getName();
        if (appDir != null) {
          myAppDirPath = appDir.getPath();
          RebarConfigUtil.calcApps(appDir, myApps);
          for (String app : myApps){
            addPath(appDir, FileUtil.join(app, "src"), mySourcePaths);
            addPath(appDir, FileUtil.join(app, "test"), myTestPaths);
            addPath(appDir, FileUtil.join(app, "include"), myIncludePaths);
          }
        }
      }
      else {
        VirtualFile appResourceFile = RebarConfigUtil.findAppResourceFile(myRoot);
        assert appResourceFile != null;
        addDependenciesFromAppFile(appResourceFile);
        String appName = StringUtil.trimEnd(StringUtil.trimEnd(appResourceFile.getName(), ".src"), ".app");
        myApps.add(appName);
        myName = appName;
        VirtualFile ebinDir = myRoot.findChild("ebin");
        if (ebinDir != null) {
          HashSet<VirtualFile> files = findAppFileFromEbin(ebinDir);
          for (VirtualFile file : files) {
            addDependenciesFromAppFile(file);
          }
        }
        addPath(myRoot, "src", mySourcePaths);
        addPath(myRoot, "test", myTestPaths);
        addPath(myRoot, "include", myIncludePaths);
      }

    });

  }

  private static HashSet<VirtualFile> findAppFileFromEbin(VirtualFile ebinRoot) {
    HashSet<VirtualFile> files = new HashSet<>();
    for (VirtualFile file : ebinRoot.getChildren()) {
      if (!file.isDirectory() && file.getName().endsWith("ebin")) files.add(file);
    }
    return files;
  }




  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public VirtualFile getRoot() {
    return myRoot;
  }

  @NotNull
  public Set<String> getDeps() {
    return myDeps;
  }

  public Set<VirtualFile> getIncludePaths() {
    return myIncludePaths;
  }

  public void setIdeaModuleFile(@Nullable VirtualFile ideaModuleFile) {
    myIdeaModuleFile = ideaModuleFile;
  }

  @Nullable
  public VirtualFile getIdeaModuleFile() {
    return myIdeaModuleFile;
  }

  public Module getModule() {
    return myModule;
  }

  public void setModule(Module module) {
    myModule = module;
  }

  public Set<String> getParseTransforms() {
    return myParseTransforms;
  }

  @Override
  public String toString() {
    return myName + " (" + myRoot.getPath() + ')';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ImportedOtpApp that = (ImportedOtpApp) o;

    if (!myName.equals(that.myName)) return false;
    if (!myRoot.equals(that.myRoot)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myRoot.hashCode();
    return result;
  }

  private void addInfoFromRebarConfig() {
    VirtualFile rebarConfig = myRoot.findChild("rebar.config");
    ErlangFile rebarConfigPsi = rebarConfig != null ? ErlangTermFileUtil.createPsi(rebarConfig) : null;
    if (rebarConfigPsi == null) return;
    addDependenciesFromRebarConfig(rebarConfigPsi);
    addIncludePathsFromRebarConfig(rebarConfigPsi);
    addParseTransformsFromRebarConfig(rebarConfigPsi);
    addExtraSourceDirFromRebarConfig(rebarConfigPsi);
  }

  private void addExtraSourceDirFromRebarConfig(ErlangFile rebarConfig) {
    RebarConfigUtil.getExtraSrcDirs(rebarConfig)
                   .forEach(path -> addPath(myRoot, path, mySourcePaths));
  }

  private void addDependenciesFromAppFile(@NotNull VirtualFile appFile) {
    ErlangFile appConfigPsi = ErlangTermFileUtil.createPsi(appFile);
    List<ErlangTupleExpression> applicationDescriptors = ErlangTermFileUtil.getConfigSections(appConfigPsi, "application");
    ErlangListExpression appAttributes = PsiTreeUtil.getChildOfType(ContainerUtil.getFirstItem(applicationDescriptors), ErlangListExpression.class);
    ErlangTermFileUtil.processConfigSection(appAttributes, "applications", deps -> {
      ErlangListExpression dependencyAppsList = deps instanceof ErlangListExpression ? (ErlangListExpression) deps : null;
      if (dependencyAppsList != null) {
        for (ErlangExpression depExpression : dependencyAppsList.getExpressionList()) {
          ErlangQAtom depApp = PsiTreeUtil.getChildOfType(depExpression, ErlangQAtom.class);
          ErlangAtom appNameAtom = depApp != null ? depApp.getAtom() : null;
          if (appNameAtom != null) {
            myDeps.add(appNameAtom.getName());
          }
        }
      }
    });
  }

  private void addDependenciesFromRebarConfig(ErlangFile rebarConfig) {
    myDeps.addAll(RebarConfigUtil.getDependencyAppNames(rebarConfig));
  }

  private void addIncludePathsFromRebarConfig(ErlangFile rebarConfig) {
    for (String includePath : RebarConfigUtil.getIncludePaths(rebarConfig)) {
      addPath(myRoot, includePath, myIncludePaths);
    }
  }

  private void addParseTransformsFromRebarConfig(ErlangFile rebarConfig) {
    myParseTransforms.addAll(RebarConfigUtil.getParseTransforms(rebarConfig));
  }

  private static void addPath(VirtualFile base, String relativeIncludePath, Set<VirtualFile> paths) {
    VirtualFile path = VfsUtilCore.findRelativeFile(relativeIncludePath, base);
    if (path != null) {
      paths.add(path);
    }
  }

  public Set<VirtualFile> getSourcePaths() {
    return mySourcePaths;
  }

  public Set<VirtualFile> getTestPaths() {
    return myTestPaths;
  }

  public Set<String> getApps() {
    return myApps;
  }

  public Boolean isRebar3() {
    return myIsRebar3;
  }

  public String getAppDirPath() {
    return myAppDirPath;
  }
}
