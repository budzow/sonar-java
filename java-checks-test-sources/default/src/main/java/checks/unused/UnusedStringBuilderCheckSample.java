package checks.unused;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class UnusedStringBuilderCheckSample {

  public void usedTerminalMethod() {
    StringBuilder sb = new StringBuilder();
    sb.append("Hello");
    sb.append("!");
    System.out.println(sb.toString());
  }

  public void usedPassedAsArgument() {
    StringBuilder sb = new StringBuilder();
    sb.append("Hello!");
    System.out.println(sb);
  }

  public String usedButInitializedSeparately() {
    // FN, because it is not implemented.
    StringBuilder sb;
    sb = new StringBuilder();
    sb.append("Hello");
    sb.append("!");
    return sb.toString();
  }

  public String usedInExpression() {
    StringBuilder inExpr = new StringBuilder();
    inExpr.append("two ");
    inExpr.append("three ");
    return "one" + inExpr + "four";
  }

  public void usedInException() {
    StringBuilder exceptionMessage = new StringBuilder();
    exceptionMessage.append("message");
    throw new IllegalStateException("message:" + exceptionMessage);
  }

  public char[] usedGetChars() {
    StringBuilder buf = new StringBuilder();
    buf.append("Hello ");
    buf.append("World!");
    char[] chars = new char[5];
    buf.getChars(6, 11, chars, 0);
    return chars;
  }

  public char usedChatAt() {
    StringBuilder buf = new StringBuilder();
    buf.append("Hello ");
    buf.append("World!");
    char c = buf.charAt(8);
    return c;
  }

  public void usedInArray() {
    StringBuilder buf = new StringBuilder();
    buf.append("Hello");
    buf.append("World!");
    System.out.println("%s: %s".formatted(new Object[]{"Message", buf}));
  }

  public void usedInMethodReference() {
    StringBuilder buf = new StringBuilder();
    buf.append("Hello");
    buf.append("World!");
    myLog(buf::toString);
  }

  private void myLog(Supplier<String> messageSupplier) {
    System.out.println(messageSupplier.get());
  }

  public void unused() {
    StringBuilder sb = new StringBuilder(); // Noncompliant {{Consume or remove this unused StringBuilder}}
//                ^^
    sb.append("Hello");
    sb.append("!");
    System.out.println("Hello!");
  }

  public void unusedNoOperationsAtAll() {
    StringBuilder sb = new StringBuilder(); // Noncompliant {{Consume or remove this unused StringBuilder}}
//                ^^
  }

  public String usedTerminalSubstring() {
    StringBuilder sb = new StringBuilder();
    sb.append("Hello!");
    return sb.substring(2);
  }

  public StringBuilder usedReturned() {
    StringBuilder sb = new StringBuilder();
    sb.append("returned");
    return sb;
  }

  public StringBuilder usedChainedReturned() {
    StringBuilder sb = new StringBuilder();
    return sb.append("returned");
  }

  public void usedChainedTwicePrinted() {
    StringBuilder sb = new StringBuilder();
    System.out.println(sb.append("one").append("two"));
  }

  public String usedChainedTwiceReturned() {
    StringBuilder sb = new StringBuilder();
    return sb.append("one").append("two").toString();
  }

  private void passedAsArg(StringBuilder fromOutside) {
    // Considered used, because the caller can invoke a terminal operation.
    fromOutside.append("good");
  }

  private void retrievedWithoutCallingTheConstructor(Supplier<StringBuilder> supplier) {
    // Considered used, because the caller can invoke a terminal operation.
    StringBuilder sb = supplier.get();
    sb.append("good");
  }

  private void assignedLhs(Supplier<StringBuilder> supplier) {
    // Considered used, because it is assigned.
    StringBuilder sb = new StringBuilder();
    sb = supplier.get();
    sb.append("assignment");
  }

  private void assignedRhs(Supplier<StringBuilder> supplier) {
    // FP, because sb escapes analysis, but we do not handle it.
    StringBuilder sb = new StringBuilder(); // Noncompliant {{Consume or remove this unused StringBuilder}}
//                ^^
    sb.append("assignment");
    StringBuilder sb2 = sb;
    System.out.println(sb2);
  }

  private String unusedConstructorWithArgument() {
    StringBuilder sb = new StringBuilder("Hello"); // Noncompliant {{Consume or remove this unused StringBuilder}}
//                ^^
    sb.append("!");
    return "Hello!";
  }

  private String unusedConstructorAndAppend() {
    // FN due to chained method, which we do not support.
    StringBuilder sb = new StringBuilder().append("Hello");
    sb.append("!");
    return "Hello!";
  }

  public void usedStringBufferTerminalMethod() {
    StringBuffer stringBuffer = new StringBuffer();
    stringBuffer.append("Hello");
    stringBuffer.append("!");
    System.out.println(stringBuffer.toString());
  }

  public void unusedStringBuffer() {
    StringBuffer stringBuffer = new StringBuffer(); // Noncompliant {{Consume or remove this unused StringBuffer}}
//               ^^^^^^^^^^^^
    stringBuffer.append("Hello");
    stringBuffer.append("!");
    System.out.println("Hello!");
  }


  private void unrelated() {
    List<Integer> list = new ArrayList<>();
    list.add(5);
  }

  String usedManyChainedCalls() {
    StringBuilder sb = new StringBuilder();
    return sb.append("a").append("b").toString();
  }

  String unusedManyChainedCalls() {
    StringBuilder sb = new StringBuilder(); // Noncompliant {{Consume or remove this unused StringBuilder}}
//                ^^
    sb.append("a").append("b");
    return "ab";
  }

 String unusedManyChainedCallsAssign() {
    StringBuilder sb = new StringBuilder(); // Noncompliant {{Consume or remove this unused StringBuilder}}
//                ^^
    StringBuilder sb2 = sb.append("a").append("b");
    return "ab";
  }

  static class UnusedField1 {
    // FN, because it is not implemented (initialization in the constructor).
    StringBuilder stringBuilder;

    UnusedField1() {
      this.stringBuilder = new StringBuilder();
    }

    void appendHello() {
      stringBuilder.append("Hello");
    }
  }

  static class UnusedFieldPrivate {
    // FN: Ideally we should report this one, but there are some FPs when analyzing fields, so we skip them.
    private StringBuilder stringBuilder = new StringBuilder();

    void appendHello() {
      stringBuilder.append("Hello");
    }
  }

  static class UnusedFieldProtected {
    // It can be used in subclass.
    protected StringBuilder stringBuilder = new StringBuilder();

    void appendHello() {
      stringBuilder.append("Hello");
    }
  }

  static class UsedFieldPrivate {
    private StringBuilder stringBuilder = new StringBuilder();

    void appendHello() {
      stringBuilder.append("Hello");
    }

    void print() {
      System.out.println(stringBuilder);
    }
  }
}
