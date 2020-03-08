package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.regex.Pattern;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  scope = DiagnosticScope.BSL,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.ERROR
  }

)
public class ThisObjectAssignDiagnostic extends AbstractVisitorDiagnostic {

  private static final Pattern thisObjectPattern = Pattern.compile(
    "(этотобъект|thisobject)",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
  );

  public ThisObjectAssignDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  public ParseTree visitLValue(BSLParser.LValueContext ctx) {

    TerminalNode identifier = ctx.IDENTIFIER();

    if (identifier == null) {
      return ctx;
    }

    if (!thisObjectPattern.matcher(identifier.getText()).matches()) {
      return ctx;
    }

    if ((documentContext.getModuleType() == ModuleType.CommonModule
      || documentContext.getModuleType() == ModuleType.FormModule)
      && documentContext.getServerContext().getConfiguration().getCompatibilityMode().getVersion() <
      DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_3.getCompatibilityMode().getVersion()) {
      return ctx;
    }

    diagnosticStorage.addDiagnostic(identifier);

    return ctx;
  }
}
