/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.parser.BSLParser;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MultilingualStringAnalyser {

  private static final String NSTR_METHOD_NAME = "^(НСтр|NStr)";
  private static final String TEMPLATE_METHOD_NAME = "^(СтрШаблон|StrTemplate)";
  private static final String NSTR_LANG_REGEX = "\\w+\\s*=\\s*['|\"{2}]";
  private static final String NSTR_LANG_CUT_REGEX = "\\s*=\\s*['|\"{2}]";
  private static final String WHITE_SPACE_REGEX = "\\s";
  private static final Pattern NSTR_METHOD_NAME_PATTERN = Pattern.compile(
    NSTR_METHOD_NAME,
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );
  private static final Pattern TEMPLATE_METHOD_NAME_PATTERN = Pattern.compile(
    TEMPLATE_METHOD_NAME,
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );
  private static final Pattern NSTR_LANG_PATTERN = Pattern.compile(
    NSTR_LANG_REGEX,
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );
  private static final Pattern NSTR_LANG_CUT_PATTERN = Pattern.compile(
    NSTR_LANG_CUT_REGEX,
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );
  private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile(
    WHITE_SPACE_REGEX,
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  private BSLParser.GlobalMethodCallContext globalMethodCallContext;
  private boolean isParentTemplate;
  private String variableName;
  private ArrayList<String> expectedLanguages;
  private Set<String> expandedMultilingualString = new HashSet<>();
  private ArrayList<String> missingLanguages = new ArrayList<>();

  public MultilingualStringAnalyser(String declaredLanguages) {

    Matcher matcher = WHITE_SPACE_PATTERN.matcher(declaredLanguages);
    this.expectedLanguages = new ArrayList<>(Arrays.asList(matcher.replaceAll("").split(",")));

  }

  private static boolean isNotMultilingualString(BSLParser.GlobalMethodCallContext globalMethodCallContext) {
    return !NSTR_METHOD_NAME_PATTERN.matcher(globalMethodCallContext.methodName().getText()).find();
  }

  private static boolean hasTemplateInParents(ParserRuleContext globalMethodCallContext) {
    ParserRuleContext parent = globalMethodCallContext.getParent();

    if (parent instanceof BSLParser.FileContext || parent instanceof BSLParser.StatementContext) {
      return false;
    }

    if (parent instanceof BSLParser.GlobalMethodCallContext && isTemplate((BSLParser.GlobalMethodCallContext) parent)) {
      return true;
    }

    return hasTemplateInParents(parent);
  }

  private static boolean isTemplate(BSLParser.GlobalMethodCallContext parent) {
    return TEMPLATE_METHOD_NAME_PATTERN.matcher(parent.methodName().getText()).find();
  }

  private static String getVariableName(BSLParser.GlobalMethodCallContext ctx) {
    BSLParser.AssignmentContext assignment = (BSLParser.AssignmentContext)
      Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_assignment);

    if (assignment != null) {
      BSLParser.LValueContext lValue = assignment.lValue();
      if (lValue != null) {
        return lValue.getText();
      }
    }

    return null;
  }

  public boolean parse(BSLParser.GlobalMethodCallContext ctx) {
    expandedMultilingualString.clear();
    missingLanguages.clear();
    isParentTemplate = false;

    if (isNotMultilingualString(ctx)) {
      return false;
    }

    globalMethodCallContext = ctx;
    isParentTemplate = hasTemplateInParents(ctx);
    variableName = getVariableName(ctx);
    expandMultilingualString();
    checkDeclaredLanguages();
    return true;
  }

  private void expandMultilingualString() {

    Matcher matcher = NSTR_LANG_PATTERN.matcher(getMultilingualString());

    while (matcher.find()) {
      Matcher cutMatcher = NSTR_LANG_CUT_PATTERN.matcher(matcher.group());
      String langKey = cutMatcher.replaceAll("");
      expandedMultilingualString.add(langKey);
    }

  }

  private String getMultilingualString() {
    return globalMethodCallContext.doCall().callParamList().callParam(0).getText();
  }

  private void checkDeclaredLanguages() {
    if (expandedMultilingualString.isEmpty()) {
      missingLanguages = new ArrayList<>(expectedLanguages);
      return;
    }

    for (String lang : expectedLanguages) {
      if (!expandedMultilingualString.contains(lang)) {
        missingLanguages.add(lang);
      }
    }
  }

  public boolean hasNotAllDeclaredLanguages() {
    return !missingLanguages.isEmpty();
  }

  public String getMissingLanguages() {
    return missingLanguages.toString();
  }

  public boolean isParentTemplate() {
    return isParentTemplate || istVariableUsingInTemplate();
  }

  private boolean istVariableUsingInTemplate() {
    if (variableName == null) {
      return false;
    }

    BSLParser.CodeBlockContext codeBlock = getCodeBlock();

    if (codeBlock == null) {
      return false;
    }

    return Trees.findAllRuleNodes(codeBlock, BSLParser.RULE_globalMethodCall)
      .stream()
      .filter(node ->
        ((BSLParser.GlobalMethodCallContext) node).getStart().getLine() > globalMethodCallContext.getStart().getLine())
      .filter(node -> isTemplate((BSLParser.GlobalMethodCallContext) node))
      .map(node -> ((BSLParser.GlobalMethodCallContext) node).doCall().callParamList())
      .filter(Objects::nonNull)
      .map(BSLParser.CallParamListContext::callParam)
      .filter(cp -> !cp.isEmpty())
      .anyMatch(cp -> cp.stream().anyMatch(p -> p.getText().equalsIgnoreCase(variableName)));
  }

  private BSLParser.CodeBlockContext getCodeBlock() {
    return (BSLParser.CodeBlockContext) Trees.getAncestorByRuleIndex(
      globalMethodCallContext,
      BSLParser.RULE_codeBlock
    );
  }

}
