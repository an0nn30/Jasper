package dev.jasper.sdk.ui;

/** Semantic syntax roles, classified by the editor plugin.
 * @since 0.12.0
 */
public enum SyntaxRole {
    /** Reserved language word. */ KEYWORD,
    /** Type or class name. */ TYPE,
    /** Function or method name. */ FUNCTION,
    /** Variable or parameter. */ VARIABLE,
    /** Named constant. */ CONSTANT,
    /** Numeric literal. */ NUMBER,
    /** String or character literal. */ STRING,
    /** Ordinary comment. */ COMMENT,
    /** Documentation comment. */ DOC_COMMENT,
    /** Annotation or decorator. */ ANNOTATION,
    /** Operator. */ OPERATOR,
    /** Delimiter or separator. */ PUNCTUATION,
    /** Markup tag. */ TAG,
    /** Markup attribute. */ ATTRIBUTE,
    /** Regular expression. */ REGEX,
    /** Invalid or erroneous token. */ ERROR
}
