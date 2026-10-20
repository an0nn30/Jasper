package dev.jasper.editor.syntax;

import dev.jasper.sdk.ui.SyntaxRole;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

/** Shared semantic token roles for source text and isolated preview lexers. */
public final class TokenRoles {
    private TokenRoles() { }

    /** Returns a semantic role, or null for ordinary unstyled text. */
    public static SyntaxRole role(int token) {
        return switch (token) {
            case TokenTypes.RESERVED_WORD, TokenTypes.RESERVED_WORD_2, TokenTypes.PREPROCESSOR -> SyntaxRole.KEYWORD;
            case TokenTypes.DATA_TYPE -> SyntaxRole.TYPE;
            case TokenTypes.FUNCTION -> SyntaxRole.FUNCTION;
            case TokenTypes.VARIABLE -> SyntaxRole.VARIABLE;
            case TokenTypes.LITERAL_BOOLEAN -> SyntaxRole.CONSTANT;
            case TokenTypes.LITERAL_NUMBER_DECIMAL_INT, TokenTypes.LITERAL_NUMBER_FLOAT,
                TokenTypes.LITERAL_NUMBER_HEXADECIMAL -> SyntaxRole.NUMBER;
            case TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, TokenTypes.LITERAL_CHAR, TokenTypes.LITERAL_BACKQUOTE -> SyntaxRole.STRING;
            case TokenTypes.COMMENT_EOL, TokenTypes.COMMENT_MULTILINE -> SyntaxRole.COMMENT;
            case TokenTypes.COMMENT_DOCUMENTATION, TokenTypes.COMMENT_KEYWORD, TokenTypes.COMMENT_MARKUP -> SyntaxRole.DOC_COMMENT;
            case TokenTypes.ANNOTATION -> SyntaxRole.ANNOTATION;
            case TokenTypes.OPERATOR -> SyntaxRole.OPERATOR;
            case TokenTypes.SEPARATOR -> SyntaxRole.PUNCTUATION;
            case TokenTypes.MARKUP_TAG_DELIMITER, TokenTypes.MARKUP_TAG_NAME -> SyntaxRole.TAG;
            case TokenTypes.MARKUP_TAG_ATTRIBUTE, TokenTypes.MARKUP_TAG_ATTRIBUTE_VALUE -> SyntaxRole.ATTRIBUTE;
            case TokenTypes.REGEX -> SyntaxRole.REGEX;
            case TokenTypes.ERROR_IDENTIFIER, TokenTypes.ERROR_NUMBER_FORMAT,
                TokenTypes.ERROR_STRING_DOUBLE, TokenTypes.ERROR_CHAR -> SyntaxRole.ERROR;
            default -> null;
        };
    }

}
