package com.intellij.flex.uiDesigner.mxml;

import com.intellij.flex.uiDesigner.InvalidPropertyException;
import com.intellij.flex.uiDesigner.io.Amf3Types;
import com.intellij.flex.uiDesigner.io.PrimitiveAmfOutputStream;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ExpressionBinding extends Binding {
  private final JSExpression expression;

  public ExpressionBinding(JSExpression expression) {
    this.expression = expression;
  }

  @Override
  protected int getType() {
    return BindingType.EXPRESSION;
  }

  @Override
  void write(PrimitiveAmfOutputStream out, BaseWriter writer, ValueReferenceResolver valueReferenceResolver)
    throws InvalidPropertyException {
    super.write(out, writer, valueReferenceResolver);

    writeExpression(expression, out, writer, valueReferenceResolver);
  }

  private static void writeExpression(JSExpression expression, PrimitiveAmfOutputStream out, BaseWriter writer,
                                      @Nullable ValueReferenceResolver valueReferenceResolver)
    throws InvalidPropertyException {
    if (expression instanceof JSLiteralExpression) {
      writeLiteralExpression((JSLiteralExpression)expression, out, writer);
    }
    else if (expression instanceof JSObjectLiteralExpression) {
      writeObjectExpression((JSObjectLiteralExpression)expression, out, writer, valueReferenceResolver);
    }
    else if (expression instanceof JSArrayLiteralExpression) {
      writeArrayLiteralExpression((JSArrayLiteralExpression)expression, out, writer, valueReferenceResolver);
    }
    else if (expression instanceof JSNewExpression) {
      writeNewExpression((JSNewExpression)expression, out, writer, valueReferenceResolver);
    }
    else if (expression instanceof JSReferenceExpression) {
      writeReferenceExpression((JSReferenceExpression)expression, out, writer, valueReferenceResolver);
    }
    else {
      throw new UnsupportedOperationException(expression.getText());
    }
  }

  static void writeArrayLiteralExpression(JSArrayLiteralExpression expression, PrimitiveAmfOutputStream out, BaseWriter writer,
                                          @Nullable ValueReferenceResolver valueReferenceResolver) throws InvalidPropertyException {
    JSExpression[] expressions = expression.getExpressions();
    writer.writeArrayHeader(expressions.length);
    for (JSExpression item : expressions) {
      writeExpression(item, out, writer, valueReferenceResolver);
    }
  }

  private static void writeNewExpression(JSNewExpression expression, PrimitiveAmfOutputStream out, BaseWriter writer,
                                         ValueReferenceResolver valueReferenceResolver) throws InvalidPropertyException {
    JSClass jsClass = (JSClass)resolveReferenceExpression(((JSReferenceExpression)expression.getMethodExpression())).getParent();
    JSExpression[] arguments = expression.getArguments();
    writer.writeNew(jsClass.getQualifiedName(), arguments.length);
    for (JSExpression argument : arguments) {
      writeExpression(argument, out, writer, valueReferenceResolver);
    }
  }

  @NotNull
  private static PsiElement resolveReferenceExpression(JSReferenceExpression expression) throws InvalidPropertyException {
    final AccessToken token = ReadAction.start();
    final PsiElement element;
    try {
      element = expression.resolve();
    }
    finally {
      token.finish();
    }

    if (element == null) {
      throw new InvalidPropertyException(expression, "unresolved.variable.or.type", expression.getReferencedName());
    }

    return element;
  }

  private static void writeReferenceExpression(JSReferenceExpression expression, PrimitiveAmfOutputStream out, BaseWriter writer,
                                               ValueReferenceResolver valueReferenceResolver)
    throws InvalidPropertyException {
    PsiElement element = resolveReferenceExpression(expression);
    if (element instanceof JSClass) {
      writer.writeClass(((JSClass)element).getQualifiedName());
    }
    else if (element instanceof JSVariable) {
      VariableReference valueReference = valueReferenceResolver.getNullableValueReference((JSVariable)element);
      if (valueReference != null) {
        out.write(ExpressionMessageTypes.VARIABLE_REFERENCE);
        // may be already referenced, i.e. VariableReference created for this variable
        valueReference.write(out, writer, valueReferenceResolver);
        return;
      }

      writeJSVariable(((JSVariable)element), out, writer, valueReferenceResolver);
    }
    else {
      out.write(ExpressionMessageTypes.MXML_OBJECT_REFERENCE);
      valueReferenceResolver.getValueReference(expression.getReferencedName()).write(out, writer, valueReferenceResolver);
    }
  }

  static void writeJSVariable(JSVariable variable, PrimitiveAmfOutputStream out, BaseWriter writer,
                              ValueReferenceResolver valueReferenceResolver) throws InvalidPropertyException {
    JSExpression initializer = variable.getInitializer();
    if (initializer == null) {
      MxmlWriter.LOG.warn("Unsupported variable without initializer: " + variable.getParent().getText() + ", write as null");
      out.write(Amf3Types.NULL);
    }
    else {
      writeExpression(initializer, out, writer, valueReferenceResolver);
    }
  }

  private static void writeLiteralExpression(JSLiteralExpression expression, PrimitiveAmfOutputStream out, BaseWriter writer) {
    if (expression.isNumericLiteral()) {
      out.writeAmfDouble(expression.getText());
    }
    else {
      writer.writeString(StringUtil.stripQuotesAroundValue(expression.getText()));
    }
  }

  private static void writeObjectExpression(JSObjectLiteralExpression expression, PrimitiveAmfOutputStream out, BaseWriter writer,
                                            ValueReferenceResolver valueReferenceResolver) throws InvalidPropertyException {
    JSProperty[] properties = expression.getProperties();
    out.write(ExpressionMessageTypes.SIMPLE_OBJECT);
    for (JSProperty property : properties) {
      writer.write(property.getName());
      writeExpression(property.getValue(), out, writer, valueReferenceResolver);
    }
    writer.endObject();
  }
}
