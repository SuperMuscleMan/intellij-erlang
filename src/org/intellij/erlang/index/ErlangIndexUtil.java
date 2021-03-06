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

package org.intellij.erlang.index;

import com.intellij.util.indexing.FileBasedIndex;
import org.intellij.erlang.ErlangFileType;

public final class ErlangIndexUtil {
  private ErlangIndexUtil() {
  }

  public static final FileBasedIndex.InputFilter ERLANG_MODULE_FILTER = file -> file.getFileType() == ErlangFileType.MODULE;
  public static final FileBasedIndex.InputFilter ERLANG_HRL_FILTER = file -> file.getFileType() == ErlangFileType.HEADER;
  public static final FileBasedIndex.InputFilter ERLANG_ALL_FILTER = file -> file.getFileType() == ErlangFileType.MODULE
                                                                             ||file.getFileType() == ErlangFileType.TERMS;
}
