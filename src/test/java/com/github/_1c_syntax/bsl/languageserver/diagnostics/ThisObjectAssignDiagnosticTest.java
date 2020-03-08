package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

class ThisObjectAssignDiagnosticTest extends AbstractDiagnosticTest<ThisObjectAssignDiagnostic> {
  ThisObjectAssignDiagnosticTest() {
    super(ThisObjectAssignDiagnostic.class);
  }

  @Test
  void test() {

    DocumentContext documentContext = getDocumentContext();
    List<Diagnostic> diagnostics = getDiagnostics(documentContext);
    assertThat(diagnostics).hasSize(1);
    assertThat(diagnostics, true)
      .hasRange(1, 4, 14);

  }
}
