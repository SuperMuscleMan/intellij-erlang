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

package org.intellij.erlang.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import org.intellij.erlang.ErlangFileType;
import org.intellij.erlang.ErlangLanguage;
import org.intellij.erlang.ErlangTypes;
import org.intellij.erlang.parser.ErlangParserUtil;
import org.intellij.erlang.psi.*;
import org.intellij.erlang.stubs.ErlangCallbackSpecStub;
import org.intellij.erlang.stubs.ErlangFileStub;
import org.intellij.erlang.stubs.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.util.Collections.*;
import static org.intellij.erlang.psi.impl.ErlangPsiImplUtil.*;

public class ErlangFileImpl extends PsiFileBase implements ErlangFile, PsiNameIdentifierOwner {
  public ErlangFileImpl(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, ErlangLanguage.INSTANCE);
  }

  @Nullable
  @Override
  public ErlangModule getModule() {
    ErlangFileStub stub = getStub();
    if (stub != null) {
      return ArrayUtil.getFirstElement(stub.getChildrenByType(ErlangTypes.ERL_MODULE, ErlangModuleStubElementType.ARRAY_FACTORY));
    }

    List<ErlangAttribute> attributes = PsiTreeUtil.getChildrenOfTypeAsList(this, ErlangAttribute.class);
    for (ErlangAttribute attribute : attributes) {
      ErlangModule module = attribute.getModule();
      if (module != null) {
        return module;
      }
    }
    return null;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    String nameWithoutExtension = FileUtil.getNameWithoutExtension(name);

    for (ErlangAttribute moduleAttributes : getAttributes()) {
      ErlangModule module = moduleAttributes.getModule();
      if (module != null) {
        // todo: use module with dependencies scope
        if (!DumbService.isDumb(getProject())) {
          Query<PsiReference> search = ReferencesSearch.search(module, GlobalSearchScope.allScope(module.getProject()));
          for (PsiReference psiReference : search) {
            psiReference.handleElementRename(nameWithoutExtension);
          }
        }
        module.setName(nameWithoutExtension);
      }
    }

    return super.setName(name);
  }

  private CachedValue<List<ErlangRule>> myRulesValue =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<List<ErlangRule>>() {
      @Override
      public Result<List<ErlangRule>> compute() {
        return Result.create(unmodifiableList(calcRules()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<List<ErlangFunction>> myFunctionValue =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<List<ErlangFunction>>() {
      @Override
      public Result<List<ErlangFunction>> compute() {
        return Result.create(unmodifiableList(calcFunctions()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<List<ErlangImportFunction>> myImportValue =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<List<ErlangImportFunction>>() {
      @Override
      public Result<List<ErlangImportFunction>> compute() {
        return Result.create(unmodifiableList(calcImports()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<Set<ErlangFunction>> myExportedFunctionValue =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<Set<ErlangFunction>>() {
      @Override
      public Result<Set<ErlangFunction>> compute() {
        return Result.create(unmodifiableSet(calcExportedFunctions()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<List<ErlangAttribute>> myAttributeValue =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<List<ErlangAttribute>>() {
      @Override
      public Result<List<ErlangAttribute>> compute() {
        return Result.create(unmodifiableList(calcAttributes()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<List<ErlangRecordDefinition>> myRecordValue =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<List<ErlangRecordDefinition>>() {
      @Override
      public Result<List<ErlangRecordDefinition>> compute() {
        return Result.create(unmodifiableList(calcRecords()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<List<ErlangInclude>> myIncludeValue =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<List<ErlangInclude>>() {
      @Override
      public Result<List<ErlangInclude>> compute() {
        return Result.create(unmodifiableList(calcIncludes()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<List<ErlangIncludeLib>> myIncludeLibValue =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<List<ErlangIncludeLib>>() {
      @Override
      public Result<List<ErlangIncludeLib>> compute() {
        return Result.create(unmodifiableList(calcIncludeLibs()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<MultiMap<String, ErlangFunction>> myFunctionsMap =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<MultiMap<String, ErlangFunction>>() {
      @Override
      public Result<MultiMap<String, ErlangFunction>> compute() {
        MultiMap<String, ErlangFunction> map = new MultiMap<String, ErlangFunction>();
        for (ErlangFunction function : getFunctions()) {
          map.putValue(function.getName(), function);
        }
        return Result.create(map, ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<MultiMap<String, ErlangImportFunction>> myImportsMap =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<MultiMap<String, ErlangImportFunction>>() {
      @Override
      public Result<MultiMap<String, ErlangImportFunction>> compute() {
        MultiMap<String, ErlangImportFunction> map = new MultiMap<String, ErlangImportFunction>();
        for (ErlangImportFunction importFunction : getImportedFunctions()) {
          map.putValue(ErlangPsiImplUtil.getName(importFunction), importFunction);
        }
        return Result.create(map, ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<Map<String, ErlangRecordDefinition>> myRecordsMap =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<Map<String, ErlangRecordDefinition>>() {
      @Override
      public Result<Map<String, ErlangRecordDefinition>> compute() {
        Map<String, ErlangRecordDefinition> map = new THashMap<String, ErlangRecordDefinition>();
        for (ErlangRecordDefinition record : getRecords()) {
          String recordName = record.getName();
          if (!map.containsKey(recordName)) {
            map.put(recordName, record);
          }
        }
        return Result.create(map, ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<List<ErlangMacrosDefinition>> myMacrosValue =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<List<ErlangMacrosDefinition>>() {
      @Override
      public Result<List<ErlangMacrosDefinition>> compute() {
        return Result.create(unmodifiableList(calcMacroses()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<Map<String, ErlangMacrosDefinition>> myMacrosesMap =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<Map<String, ErlangMacrosDefinition>>() {
      @Override
      public Result<Map<String, ErlangMacrosDefinition>> compute() {
        Map<String, ErlangMacrosDefinition> map = new THashMap<String, ErlangMacrosDefinition>();
        for (ErlangMacrosDefinition macros : getMacroses()) {
          String macrosName = ErlangPsiImplUtil.getName(macros);
          if (!map.containsKey(macrosName)) {
            map.put(macrosName, macros);
          }
        }
        return Result.create(map, ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<List<ErlangTypeDefinition>> myTypeValue =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<List<ErlangTypeDefinition>>() {
      @Override
      public Result<List<ErlangTypeDefinition>> compute() {
        return Result.create(unmodifiableList(calcTypes()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<Map<String, ErlangTypeDefinition>> myTypeMap =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<Map<String, ErlangTypeDefinition>>() {
      @Override
      public Result<Map<String, ErlangTypeDefinition>> compute() {
        Map<String, ErlangTypeDefinition> map = new THashMap<String, ErlangTypeDefinition>();
        for (ErlangTypeDefinition type : getTypes()) {
          String mName = type.getName();
          if (!map.containsKey(mName)) {
            map.put(mName, type);
          }
        }
        return Result.create(map, ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<Map<String, ErlangCallbackSpec>> myCallbackMap =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<Map<String, ErlangCallbackSpec>>() {
      @Nullable
      @Override
      public Result<Map<String, ErlangCallbackSpec>> compute() {
        return Result.create(unmodifiableMap(calcCallbacks()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<List<ErlangBehaviour>> myBehavioursValue =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<List<ErlangBehaviour>>() {
      @Override
      public Result<List<ErlangBehaviour>> compute() {
        return Result.create(unmodifiableList(calcBehaviours()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<Collection<ErlangCallbackFunction>> myOptionalCallbacks =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<Collection<ErlangCallbackFunction>>() {
      @Override
      public Result<Collection<ErlangCallbackFunction>> compute() {
        return Result.create(unmodifiableCollection(calcOptionalCallbacks()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<List<ErlangSpecification>> mySpecificationsValue =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<List<ErlangSpecification>>() {
      @Override
      public Result<List<ErlangSpecification>> compute() {
        return Result.create(unmodifiableList(calcSpecifications()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<Boolean> myExportAll =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<Boolean>() {
      @Override
      public Result<Boolean> compute() {
        return Result.create(calcExportAll(), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<Boolean> myNoAutoImportAll =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<Boolean>() {
      @Override
      public Result<Boolean> compute() {
        return Result.create(calcNoAutoImportAll(), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<Set<String>> myExportedFunctionsSignatures =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<Set<String>>() {
      @Override
      public Result<Set<String>> compute() {
        return Result.create(unmodifiableSet(calcExportedSignatures()), ErlangFileImpl.this);
      }
    }, false);
  private CachedValue<Set<String>> myNoAutoImportFunctionsSignatures =
    CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<Set<String>>() {
      @Override
      public Result<Set<String>> compute() {
        return Result.create(unmodifiableSet(calcNoAutoImportSignatures()), ErlangFileImpl.this);
      }
    }, false);

  @NotNull
  @Override
  public FileType getFileType() {
    ErlangFileType type = ErlangFileType.getFileType(getName());
    return type != null ? type : ErlangFileType.MODULE;
  }

  @Nullable
  public ErlangFileStub getStub() {
    StubElement stub = super.getStub();
    if (stub == null) return null;
    return (ErlangFileStub) stub;
  }

  @Override
  public boolean isExported(@NotNull String signature) {
    if (isExportedAll()) return true;
    return myExportedFunctionsSignatures.getValue().contains(signature);
  }

  @Override
  public boolean isNoAutoImport(@NotNull String name, int arity) {
    if (isNoAutoImportAll()) return true;
    return myNoAutoImportFunctionsSignatures.getValue().contains(name + "/" + arity);
  }

  @NotNull
  private Set<String> calcExportedSignatures() {
    Set<String> result = ContainerUtil.newHashSet();
    for (ErlangAttribute attribute : getAttributes()) {
      ErlangExport export = attribute.getExport();
      ErlangExportFunctions exportFunctions = export != null ? export.getExportFunctions() : null;
      if (exportFunctions == null) continue;
      List<ErlangExportFunction> list = exportFunctions.getExportFunctionList();
      for (ErlangExportFunction exportFunction : list) {
        PsiElement integer = exportFunction.getInteger();
        if (integer == null) continue;
        String s = ErlangPsiImplUtil.getExportFunctionName(exportFunction) + "/" + integer.getText();
        result.add(s);
      }
    }
    return result;
  }

  @NotNull
  private List<ErlangExpression> getCompileDirectiveExpressions() {
    List<ErlangExpression> result = ContainerUtil.newArrayList();
    for (ErlangAttribute attribute : getAttributes()) {
      ErlangAtomAttribute atomAttribute = attribute.getAtomAttribute();
      if (atomAttribute != null && "compile".equals(atomAttribute.getName()) && atomAttribute.getAttrVal() != null) {
        result.addAll(atomAttribute.getAttrVal().getExpressionList());
      }
    }
    return result;
  }

  @NotNull
  private Set<String> calcNoAutoImportSignatures() {
    Set<String> result = ContainerUtil.newHashSet();
    for (ErlangExpression expression : getCompileDirectiveExpressions()) {
      if (expression instanceof ErlangListExpression) {
        for (ErlangExpression tuple : ((ErlangListExpression) expression).getExpressionList()) {
          if (tuple instanceof ErlangTupleExpression) {
            result.addAll(getNoAutoImportFunctionSignaturesFromTuple((ErlangTupleExpression) tuple));
          }
        }
      }
      else if (expression instanceof ErlangTupleExpression) {
        result.addAll(getNoAutoImportFunctionSignaturesFromTuple((ErlangTupleExpression) expression));
      }
    }
    return result;
  }

  @NotNull
  private static Set<String> getNoAutoImportFunctionSignaturesFromTuple(@Nullable ErlangTupleExpression tupleExpression) {
    final Set<String> result = ContainerUtil.newHashSet();
    if (tupleExpression == null || tupleExpression.getExpressionList().size() != 2) return result;
    ErlangExpression first = ContainerUtil.getFirstItem(tupleExpression.getExpressionList());
    ErlangExpression second = ContainerUtil.getLastItem(tupleExpression.getExpressionList());
    if (!(first instanceof ErlangMaxExpression)
      || !(second instanceof ErlangListExpression)
      || !"no_auto_import".equals(getAtomName((ErlangMaxExpression) first))) {
      return result;
    }
    second.accept(new ErlangRecursiveVisitor() {
      @Override
      public void visitAtomWithArityExpression(@NotNull ErlangAtomWithArityExpression o) {
        result.add(createFunctionPresentation(o));
      }

      @Override
      public void visitTupleExpression(@NotNull ErlangTupleExpression o) {
        List<ErlangExpression> exprs = o.getExpressionList();
        if (exprs.size() != 2) return;

        String functionName = getAtomName(ObjectUtils.tryCast(exprs.get(0), ErlangMaxExpression.class));
        int functionArity = getArity(ObjectUtils.tryCast(exprs.get(1), ErlangMaxExpression.class));
        if (functionName == null || functionArity == -1) return;

        result.add(createFunctionPresentation(functionName, functionArity));
      }
    });
    return result;
  }

  @Override
  public boolean isExportedAll() {
    //TODO do we use stubs?
    ErlangFileStub stub = getStub();
    if (stub != null) {
      return stub.isExportAll();
    }
    return myExportAll.getValue();
  }

  private boolean containsCompileDirectiveWithOption(@NotNull String option) {
    for (ErlangExpression expression : getCompileDirectiveExpressions()) {
      if (expression instanceof ErlangListExpression) {
        for (ErlangExpression e : ((ErlangListExpression) expression).getExpressionList()) {
          if (e instanceof ErlangMaxExpression && option.equals(getAtomName((ErlangMaxExpression) e))) {
            return true;
          }
        }
      }
      else if (expression instanceof ErlangMaxExpression && option.equals(getAtomName((ErlangMaxExpression) expression))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isNoAutoImportAll() {
    return myNoAutoImportAll.getValue();
  }

  @Override
  public boolean isBehaviour() {
    ErlangFileStub stub = getStub();
    if (stub != null) return stub.isBehaviour();

    ErlangFunction behaviourInfo = getFunction("behaviour_info", 1);
    return behaviourInfo != null && behaviourInfo.isExported() || !getCallbackMap().isEmpty();
  }

  private boolean calcNoAutoImportAll() {
    return containsCompileDirectiveWithOption("no_auto_import");
  }

  private boolean calcExportAll() {
    return containsCompileDirectiveWithOption("export_all");
  }

  @NotNull
  @Override
  public List<ErlangRule> getRules() {
    return myRulesValue.getValue();
  }

  @NotNull
  @Override
  public List<ErlangAttribute> getAttributes() {
    return myAttributeValue.getValue();
  }

  @Nullable
  @Override
  public ErlangCallbackSpec getCallbackByName(@NotNull String fullName) {
    return getCallbackMap().get(fullName);
  }

  @NotNull
  @Override
  public Map<String, ErlangCallbackSpec> getCallbackMap() {
    //TODO do we use stubs?
    ErlangFileStub stub = getStub();
    if (stub != null) {
      Map<String, ErlangCallbackSpec> callbacksMap = new LinkedHashMap<String, ErlangCallbackSpec>();
      for (StubElement child : stub.getChildrenStubs()) {
        if (child instanceof ErlangCallbackSpecStub) {
          String name = ((ErlangCallbackSpecStub) child).getName();
          int arity = ((ErlangCallbackSpecStub) child).getArity();
          callbacksMap.put(name + "/" + arity, ((ErlangCallbackSpecStub) child).getPsi());
        }
      }
      return callbacksMap;
    }

    return myCallbackMap.getValue();
  }

  @NotNull
  private Map<String, ErlangCallbackSpec> calcCallbacks() {
    Map<String, ErlangCallbackSpec> callbacksMap = new LinkedHashMap<String, ErlangCallbackSpec>();

    for (ErlangAttribute a : getAttributes()) {
      ErlangCallbackSpec spec = a.getCallbackSpec();
      if (spec != null) {
        String name = ErlangPsiImplUtil.getCallbackSpecName(spec);
        int arity = ErlangPsiImplUtil.getCallBackSpecArguments(spec).size();
        callbacksMap.put(name + "/" + arity, spec);
      }
    }
    return callbacksMap;
  }
  
  @NotNull
  @Override
  public List<ErlangFunction> getFunctions() {
    return myFunctionValue.getValue();
  }

  @NotNull
  @Override
  public Collection<ErlangFunction> getExportedFunctions() {
    return myExportedFunctionValue.getValue();
  }

  private Set<ErlangFunction> calcExportedFunctions() {
    return ContainerUtil.map2SetNotNull(getFunctions(), new Function<ErlangFunction, ErlangFunction>() {
      @Nullable
      @Override
      public ErlangFunction fun(ErlangFunction f) {
        return f.isExported() ? f : null;
      }
    });
  }

  @Nullable
  @Override
  public ErlangFunction getFunction(@NotNull String name, int argsCount) {
    MultiMap<String, ErlangFunction> value = myFunctionsMap.getValue();
    return getFunctionFromMap(value, name, argsCount);
  }

  @Override
  @NotNull
  public Collection<ErlangFunction> getFunctionsByName(@NotNull String name) {
    return myFunctionsMap.getValue().get(name);
  }

  @Nullable
  private static ErlangFunction getFunctionFromMap(MultiMap<String, ErlangFunction> value, String name, final int argsCount) {
    Collection<ErlangFunction> candidates = value.get(name);

    return ContainerUtil.getFirstItem(ContainerUtil.filter(candidates, new Condition<ErlangFunction>() {
      @Override
      public boolean value(ErlangFunction erlangFunction) {
        return erlangFunction.getArity() == argsCount;
      }
    }));
  }

  @NotNull
  @Override
  public List<ErlangRecordDefinition> getRecords() {
    return myRecordValue.getValue();
  }

  @NotNull
  @Override
  public List<ErlangTypeDefinition> getTypes() {
    return myTypeValue.getValue();
  }

  private List<ErlangTypeDefinition> calcTypes() {
    return calcChildren(ErlangTypeDefinition.class,
                        ErlangTypes.ERL_TYPE_DEFINITION,
                        ErlangTypeDefinitionElementType.ARRAY_FACTORY);
  }

  @Override
  public ErlangTypeDefinition getType(@NotNull String name) {
    return myTypeMap.getValue().get(name);
  }

  @NotNull
  @Override
  public List<ErlangMacrosDefinition> getMacroses() {
    return myMacrosValue.getValue();
  }

  private List<ErlangMacrosDefinition> calcMacroses() {
    return calcChildren(ErlangMacrosDefinition.class,
                        ErlangTypes.ERL_MACROS_DEFINITION,
                        ErlangMacrosDefinitionElementType.ARRAY_FACTORY);
  }

  @Override
  public ErlangMacrosDefinition getMacros(@NotNull String name) {
    return myMacrosesMap.getValue().get(name);
  }

  private List<ErlangRecordDefinition> calcRecords() {
    return calcChildren(ErlangRecordDefinition.class,
                        ErlangTypes.ERL_RECORD_DEFINITION,
                        ErlangRecordDefinitionElementType.ARRAY_FACTORY);
  }

  @NotNull
  @Override
  public List<ErlangInclude> getIncludes() {
    return myIncludeValue.getValue();
  }

  @NotNull
  @Override
  public List<ErlangIncludeLib> getIncludeLibs() {
    return myIncludeLibValue.getValue();
  }

  private List<ErlangInclude> calcIncludes() {
    return calcChildren(ErlangInclude.class,
                        ErlangTypes.ERL_INCLUDE,
                        ErlangIncludeElementType.ARRAY_FACTORY);
  }

  private List<ErlangIncludeLib> calcIncludeLibs() {
    return calcChildren(ErlangIncludeLib.class,
                        ErlangTypes.ERL_INCLUDE_LIB,
                        ErlangIncludeLibElementType.ARRAY_FACTORY);
  }

  @NotNull
  @Override
  public List<ErlangBehaviour> getBehaviours() {
    return myBehavioursValue.getValue();
  }

  private List<ErlangBehaviour> calcBehaviours() {
    ErlangFileStub stub = getStub();
    if (stub != null) {
      return getChildrenByType(stub, ErlangTypes.ERL_BEHAVIOUR, ErlangBehaviourStubElementType.ARRAY_FACTORY);
    }

    return ContainerUtil.mapNotNull(getAttributes(), new Function<ErlangAttribute, ErlangBehaviour>() {
      @Nullable
      @Override
      public ErlangBehaviour fun(ErlangAttribute attribute) {
        return attribute.getBehaviour();
      }
    });
  }

  @NotNull
  @Override
  public Collection<ErlangCallbackFunction> getOptionalCallbacks() {
    return myOptionalCallbacks.getValue();
  }

  @NotNull
  private Collection<ErlangCallbackFunction> calcOptionalCallbacks() {
    ErlangFileStub stub = getStub();
    if (stub != null) {
      return getChildrenByType(stub, ErlangTypes.ERL_CALLBACK_FUNCTION,
                               ErlangCallbackFunctionStubElementType.ARRAY_FACTORY);
    }

    List<ErlangCallbackFunction> optionalCallbacks = ContainerUtil.newArrayList();
    for (ErlangAttribute attr : getAttributes()) {
      ErlangOptionalCallbacks callbacks = attr.getOptionalCallbacks();
      ErlangOptionalCallbackFunctions opts = callbacks != null ? callbacks.getOptionalCallbackFunctions() : null;
      optionalCallbacks.addAll(opts != null ? opts.getCallbackFunctionList() : ContainerUtil.<ErlangCallbackFunction>emptyList());
    }
    return optionalCallbacks;
  }

  @NotNull
  @Override
  public List<ErlangSpecification> getSpecifications() {
    return mySpecificationsValue.getValue();
  }

  private List<ErlangSpecification> calcSpecifications() {
    ErlangFileStub stub = getStub();
    if (stub != null) {
      return getChildrenByType(stub, ErlangTypes.ERL_SPECIFICATION, ErlangSpecificationElementType.ARRAY_FACTORY);
    }

    return ContainerUtil.mapNotNull(getAttributes(), new Function<ErlangAttribute, ErlangSpecification>() {
      @Nullable
      @Override
      public ErlangSpecification fun(ErlangAttribute attribute) {
        return attribute.getSpecification();
      }
    });
  }

  @Override
  public ErlangRecordDefinition getRecord(String name) {
    return myRecordsMap.getValue().get(name);
  }

  @Nullable
  public ErlangImportFunction getImportedFunction(String name, final int arity) {
    MultiMap<String, ErlangImportFunction> importsMap = myImportsMap.getValue();
    Collection<ErlangImportFunction> importFunctions = importsMap.get(name);
    return ContainerUtil.find(importFunctions, new Condition<ErlangImportFunction>() {
      @Override
      public boolean value(ErlangImportFunction importFunction) {
        return arity == ErlangPsiImplUtil.getArity(importFunction);
      }
    });
  }

  @NotNull
  @Override
  public List<ErlangImportFunction> getImportedFunctions() {
    return myImportValue.getValue();
  }

  private List<ErlangImportFunction> calcImports() {
    ArrayList<ErlangImportFunction> result = new ArrayList<ErlangImportFunction>();
    for (ErlangAttribute attribute : getAttributes()) {
      ErlangImportDirective importDirective = attribute.getImportDirective();
      ErlangImportFunctions importFunctions = importDirective != null ? importDirective.getImportFunctions() : null;
      List<ErlangImportFunction> functions = importFunctions != null ? importFunctions.getImportFunctionList() : null;
      ContainerUtil.addAll(result, ContainerUtil.notNullize(functions));
    }
    return result;
  }

  private List<ErlangFunction> calcFunctions() {
    return calcChildren(ErlangFunction.class,
                        ErlangTypes.ERL_FUNCTION,
                        ErlangFunctionStubElementType.ARRAY_FACTORY);
  }

  private List<ErlangAttribute> calcAttributes() {
    return collectChildrenDummyAware(ErlangAttribute.class);
  }

  private List<ErlangRule> calcRules() {
    return collectChildrenDummyAware(ErlangRule.class);
  }

  @Override
  public void addDeclaredParseTransforms(@NotNull Set<String> parseTransforms) {
    ErlangFileStub stub = getStub();
    if (stub != null) {
      String fromStub = stub.getParseTransforms();
      List<String> split = fromStub != null ? StringUtil.split(fromStub, ",") : ContainerUtil.<String>emptyList();
      parseTransforms.addAll(split);
      return;
    }
    for (ErlangAttribute attribute : getAttributes()) {
      ErlangAtomAttribute atomAttribute = attribute.getAtomAttribute();
      String attributeName = null != atomAttribute ? atomAttribute.getName() : null;
      ErlangAttrVal attrVal = atomAttribute != null ? atomAttribute.getAttrVal() : null;
      if (!"compile".equals(attributeName) || attrVal == null) continue;

      for (ErlangExpression expression : attrVal.getExpressionList()) {
        //TODO support macros
        if (expression instanceof ErlangListExpression) {
          ErlangPsiImplUtil.extractParseTransforms((ErlangListExpression) expression, parseTransforms);
        }
        if (expression instanceof ErlangTupleExpression) {
          ErlangPsiImplUtil.extractParseTransforms((ErlangTupleExpression) expression, parseTransforms);
        }
      }
    }
  }

  @Nullable
  @Override
  public PsiElement getNameIdentifier() {
    return null; // hack for inplace rename: InplaceRefactoring#getVariable()
  }

  private <T extends StubBasedPsiElement<?>> List<T> calcChildren(@NotNull Class<T> clazz,
                                                                  @NotNull IElementType elementType,
                                                                  @NotNull ArrayFactory<T> arrayFactory) {
    ErlangFileStub stub = getStub();
    return stub != null ? getChildrenByType(stub, elementType, arrayFactory) : collectChildrenDummyAware(clazz);
  }

  @NotNull
  private <T extends PsiElement> List<T> collectChildrenDummyAware(@NotNull final Class<T> clazz) {
    final List<T> result = ContainerUtil.newArrayList();
    processChildrenDummyAware(this, new Processor<PsiElement>() {
      @Override
      public boolean process(PsiElement element) {
        if (clazz.isInstance(element)) {
          //noinspection unchecked
          result.add((T)element);
        }
        return true;
      }
    });
    return result;
  }

  private static boolean processChildrenDummyAware(ErlangFileImpl file, final Processor<PsiElement> processor) {
    return new Processor<PsiElement>() {
      @Override
      public boolean process(PsiElement psiElement) {
        for (PsiElement child = psiElement.getFirstChild(); child != null; child = child.getNextSibling()) {
          if (child instanceof ErlangParserUtil.DummyBlock) {
            if (!process(child)) return false;
          }
          else if (!processor.process(child)) return false;
        }
        return true;
      }
    }.process(file);
  }

  @NotNull
  private static <E extends StubBasedPsiElement<?>> List<E> getChildrenByType(@NotNull ErlangFileStub stub,
                                                                              @NotNull IElementType elementType,
                                                                              @NotNull ArrayFactory<E> arrayFactory) {
    return ContainerUtil.list(stub.getChildrenByType(elementType, arrayFactory));
  }
}
