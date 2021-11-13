package edu.kit.compiler.parser;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.Token;
import edu.kit.compiler.data.TokenType;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.Operator.BinaryOperator;
import edu.kit.compiler.data.Operator.UnaryOperator;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.data.ast_nodes.StatementNode;
import edu.kit.compiler.data.ast_nodes.ClassNode.ClassNodeField;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.UnaryExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.BinaryExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.ValueExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.ValueExpressionType;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.ArrayAccessExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.FieldAccessExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.MethodInvocationExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.NewArrayExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.NewObjectExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.IdentifierExpressionNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.MethodNodeParameter;
import edu.kit.compiler.data.ast_nodes.MethodNode.MethodNodeRest;
import edu.kit.compiler.data.ast_nodes.MethodNode.StaticMethodNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.BlockStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.LocalVariableDeclarationStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.IfStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.WhileStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ReturnStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ExpressionStatementNode;
import edu.kit.compiler.io.BufferedLookaheadIterator;
import edu.kit.compiler.io.LookaheadIterator;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.parser.OperatorInformation.Associativity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static edu.kit.compiler.data.TokenType.*;

/**
 * Parses a token stream.
 */
public class Parser {
    private LookaheadIterator<Token> tokenStream;

    /**
     * Creates a parser from a LookaheadIterator.
     * 
     * @param tokenStream LookaheadIterator of tokens.
     */
    public Parser(LookaheadIterator<Token> tokenStream) {
        this.tokenStream = tokenStream;
    }

    /**
     * Creates a parser that reads input tokens from the given lexer.
     * 
     * @param lexer The lexer.
     */
    public Parser(Lexer lexer) {
        this.tokenStream = new BufferedLookaheadIterator<>(lexer);
    }

    /**
     * Parses the input.
     * 
     * @throws ParseException if a syntax error is encountered.
     */
    public ProgramNode parse() {
        List<ClassNode> classes = new ArrayList<>();
        Token programStart = tokenStream.get();
        while (tokenStream.get().getType() == TokenType.Keyword_Class) {
            classes.add(parseClass());
        }
        expect(TokenType.EndOfStream);
        return new ProgramNode(programStart.getLine(), programStart.getColumn(), classes, false);
    }

    private ClassNode parseClass() {
        List<ClassNodeField> fields = new ArrayList<>();
        List<StaticMethodNode> staticMethods = new ArrayList<>();
        List<DynamicMethodNode> dynamicMethods = new ArrayList<>();

        Token classToken = expect(TokenType.Keyword_Class);
        Token className = expect(TokenType.Identifier);
        expect(TokenType.Operator_BraceL);
        while (tokenStream.get().getType() == TokenType.Keyword_Public) {
            Token firstToken = tokenStream.get();
            tokenStream.next();
            if (tokenStream.get().getType() == TokenType.Keyword_Static) {
                // Main Method
                staticMethods.add(parseStaticMethod(firstToken.getLine(), firstToken.getColumn()));
            } else {
                DataType type = parseType();
                Token name = expect(TokenType.Identifier);
                if (tokenStream.get().getType() == TokenType.Operator_Semicolon) {
                    // Field
                    tokenStream.next();
                    fields.add(new ClassNodeField(firstToken.getLine(), firstToken.getColumn(), type, name.getIntValue().get(), false));
                } else {
                    List<MethodNodeParameter> params = new ArrayList<>();
                    // Method
                    expect(TokenType.Operator_ParenL);
                    if (tokenStream.get().getType()!= TokenType.Operator_ParenR) {
                        params = parseParameters();
                    }
                    expect(TokenType.Operator_ParenR);
                    Optional<MethodNodeRest> mRest = parseMethodRest();
                    BlockStatementNode block = parseBlock();
                    dynamicMethods.add(
                        new DynamicMethodNode(firstToken.getLine(), firstToken.getColumn(),
                            type, name.getIntValue().get(), params, mRest, block.getStatements(), false)
                    );
                }
            }
        }
        expect(TokenType.Operator_BraceR);
        return new ClassNode(classToken.getLine(), classToken.getColumn(), className.getIntValue().get(),
                             fields, staticMethods, dynamicMethods, false);
    }

    private StaticMethodNode parseStaticMethod(int line, int column) {
        // "public" is already parsed
        expect(TokenType.Keyword_Static);
        expect(TokenType.Keyword_Void);
        Token name = expect(TokenType.Identifier);
        expect(TokenType.Operator_ParenL);
        int paramLine = tokenStream.get().getLine();
        int paramColumn = tokenStream.get().getColumn();
        DataType paramType = parseType();
        Token paramName = expect(TokenType.Identifier);
        MethodNodeParameter param = new MethodNodeParameter(
            paramLine, paramColumn, paramType, paramName.getIntValue().get(), false
        );
        expect(TokenType.Operator_ParenR);
        Optional<MethodNodeRest> mRest = parseMethodRest();
        BlockStatementNode block = parseBlock();
        return new StaticMethodNode(line, column, new DataType(DataTypeClass.Void),
            name.getIntValue().get(), Arrays.asList(param), mRest, block.getStatements(), false);
    }

    private DataType parseType() {
        DataType result = parseBasicType();
        while (tokenStream.get().getType() == TokenType.Operator_BracketL) {
            tokenStream.next();
            expect(TokenType.Operator_BracketR);
            result = new DataType(result);
        }
        return result;
    }

    private Optional<MethodNode.MethodNodeRest> parseMethodRest() {
        if (tokenStream.get().getType() == TokenType.Keyword_Throws) {
            int line = tokenStream.get().getLine();
            int column = tokenStream.get().getColumn();
            tokenStream.next();
            Token throwType = expect(TokenType.Identifier);
            return Optional.of(new MethodNodeRest(line, column, throwType.getIntValue().get(), false));
        }
        return Optional.empty();
    }

    private List<MethodNodeParameter> parseParameters() {
        List<MethodNodeParameter> result = new ArrayList<>();
        result.add(parseParameter());
        while (tokenStream.get().getType() == TokenType.Operator_Comma) {
            tokenStream.next();
            result.add(parseParameter());
        }
        return result;
    }

    private MethodNodeParameter parseParameter() {
        int line = tokenStream.get().getLine();
        int column = tokenStream.get().getColumn();
        DataType type = parseType();
        Token name = expect(TokenType.Identifier);
        return new MethodNodeParameter(line, column, type, name.getIntValue().get(), false);
    }

    private BlockStatementNode parseBlock() {
        List<StatementNode> stmts = new ArrayList<>();
        Token openingBrace = expect(TokenType.Operator_BraceL);
        while (tokenStream.get().getType() != TokenType.Operator_BraceR) {
            TokenType type = tokenStream.get().getType();
            if (type == TokenType.Keyword_Int ||
                type == TokenType.Keyword_Boolean ||
                type == TokenType.Keyword_Void ||
                check(TokenType.Identifier, TokenType.Identifier) ||
                check(TokenType.Identifier, TokenType.Operator_BracketL, TokenType.Operator_BracketR)) {
                // LocalVariableDeclarationStatement
                Token firstToken = tokenStream.get();
                DataType variableType = parseType();
                Optional<ExpressionNode> expr = Optional.empty();
                Token name = expect(TokenType.Identifier);
                if (tokenStream.get().getType() == TokenType.Operator_Equal) {
                    tokenStream.next();
                    expr = Optional.of(parseExpression());
                }
                expect(TokenType.Operator_Semicolon);
                stmts.add(new LocalVariableDeclarationStatementNode(
                    firstToken.getLine(), firstToken.getColumn(), variableType, name.getIntValue().get(), expr, false
                ));
            } else if (type == TokenType.Operator_Semicolon) {
                // Empty statements in blocks are deleted
                tokenStream.next();
            } else {
                // Statement
                stmts.add(parseStatement());
            }
        }
        expect(TokenType.Operator_BraceR);
        return new BlockStatementNode(openingBrace.getLine(), openingBrace.getColumn(), stmts, false);
    }

    private StatementNode parseStatement() {
        Token token = tokenStream.get();
        switch(token.getType()) {
            case Operator_BraceL: {
                return parseBlock();
            }
            case Operator_Semicolon: {
                // EmptyStatement
                tokenStream.next();
                return new BlockStatementNode(
                    token.getLine(), token.getColumn(), Collections.emptyList(), false
                );
            }
            case Keyword_If: {
                tokenStream.next();
                expect(TokenType.Operator_ParenL);
                ExpressionNode condition = parseExpression();
                expect(TokenType.Operator_ParenR);
                StatementNode thenStmt = parseStatement();
                Optional<StatementNode> elseStmt = Optional.empty();
                if (tokenStream.get().getType() == TokenType.Keyword_Else) {
                    tokenStream.next();
                    elseStmt = Optional.of(parseStatement());
                }
                return new IfStatementNode(
                    token.getLine(), token.getColumn(), condition, thenStmt, elseStmt, false
                );
            }
            case Keyword_While: {
                tokenStream.next();
                expect(TokenType.Operator_ParenL);
                ExpressionNode condition = parseExpression();
                expect(TokenType.Operator_ParenR);
                StatementNode stmt = parseStatement();
                return new WhileStatementNode(
                    token.getLine(), token.getColumn(), condition, stmt, false
                );
            }
            case Keyword_Return: {
                tokenStream.next();
                Optional<ExpressionNode> returnVal = Optional.empty();
                if (tokenStream.get().getType() != TokenType.Operator_Semicolon) {
                    returnVal = Optional.of(parseExpression());
                }
                expect(TokenType.Operator_Semicolon);
                return new ReturnStatementNode(token.getLine(), token.getColumn(), returnVal, false);
            }
            default: {
                // Probably an ExpressionStatement
                ExpressionNode stmt = parseExpression();
                expect(TokenType.Operator_Semicolon);
                return new ExpressionStatementNode(token.getLine(), token.getColumn(), stmt, false);
            }
        }

    }

    private ExpressionNode parsePrimaryExpression() {
        Token token = tokenStream.get();
        switch(token.getType()) {
            case Keyword_Null: {
                tokenStream.next();
                return new ValueExpressionNode(
                    token.getLine(), token.getColumn(), ValueExpressionType.Null, false
                );
            }
            case Keyword_False: {
                tokenStream.next();
                return new ValueExpressionNode(
                    token.getLine(), token.getColumn(), ValueExpressionType.False, false
                );
            }
            case Keyword_True: {
                tokenStream.next();
                return new ValueExpressionNode(
                    token.getLine(), token.getColumn(), ValueExpressionType.True, false
                );
            }
            case IntegerLiteral: {
                tokenStream.next();
                return new ValueExpressionNode(
                    token.getLine(), token.getColumn(), ValueExpressionType.IntegerLiteral,
                    token.getLiteralValue().get(), false
                );
            }
            case Identifier: {
                tokenStream.next();
                if (tokenStream.get().getType() == TokenType.Operator_ParenL) {
                    // method invocation
                    tokenStream.next();
                    List<ExpressionNode> args = parseArguments();
                    expect(TokenType.Operator_ParenR);
                    return new MethodInvocationExpressionNode(
                        token.getLine(), token.getColumn(), Optional.empty(), token.getIntValue().get(), args, false
                    );
                } else {
                    // reference
                    return new IdentifierExpressionNode(
                        token.getLine(), token.getColumn(), token.getIntValue().get(), false
                    );
                }
            }
            case Keyword_This: {
                tokenStream.next();
                return new ValueExpressionNode(
                    token.getLine(), token.getColumn(), ValueExpressionType.This, false
                );
            }
            case Operator_ParenL: {
                tokenStream.next();
                ExpressionNode expr = parseExpression();
                expect(TokenType.Operator_ParenR);
                return expr;
            }
            case Keyword_New: {
                ExpressionNode expr = parseNewExpression();
                return expr;
            }
            default: {
                throw new ParseException(token, "expected primary expression");
            }
        }
    }

    private ExpressionNode parseNewExpression() {
        Token firstToken = expect(TokenType.Keyword_New);
        if (check(TokenType.Identifier, TokenType.Operator_ParenL)) {
            // NewObjectExpression
            Token typeName = tokenStream.get();
            tokenStream.next(2);
            expect(TokenType.Operator_ParenR);
            return new NewObjectExpressionNode(
                firstToken.getLine(), firstToken.getColumn(), typeName.getIntValue().get(), false
            );
        } else {
            // NewArrayExpression
            DataType type = parseBasicType();
            expect(TokenType.Operator_BracketL);
            ExpressionNode expr = parseExpression();
            expect(TokenType.Operator_BracketR);

            int dimensions = 1;
            while (tokenStream.get().getType() == TokenType.Operator_BracketL) {
                if (tokenStream.get(1).getType() == TokenType.Operator_BracketR) {
                    tokenStream.next(2);
                    dimensions++;
                } else {
                    // This is not part of the NewArrayExpression, but possibly an ArrayAccess
                    break;
                }
            }
            return new NewArrayExpressionNode(
                firstToken.getLine(), firstToken.getColumn(), type, expr, dimensions, false
            );
        }
    }

    private DataType parseBasicType() {
        Token token = tokenStream.get();
        switch(token.getType()) {
            case Identifier: {
                tokenStream.next();
                return new DataType(token.getIntValue().get());
            }
            case Keyword_Int: {
                tokenStream.next();
                return new DataType(DataTypeClass.Int);
            }
            case Keyword_Boolean: {
                tokenStream.next();
                return new DataType(DataTypeClass.Boolean);
            }
            case Keyword_Void: {
                tokenStream.next();
                return new DataType(DataTypeClass.Void);
            }
            default: {
                throw new ParseException(token, "expected int, boolean, void or identifier");
            }
        }
        
    }

    private ExpressionNode parseExpression() {
        return parseExpression(OperatorInformation.MIN_PRECEDENCE);
    }

    private ExpressionNode parseExpression(int minPrecedence) {
        ExpressionNode lhs = parseUnaryExpression();

        Optional<OperatorInformation> operator;
        while (
            (operator =
                OperatorInformation
                .getInfixOperatorInformation(tokenStream.get().getType())
            ).isPresent() &&
            operator.get().getPrecedence() >= minPrecedence
        ) {
            Token opToken = tokenStream.get();
            tokenStream.next();

            int precedence = operator.get().getPrecedence();
            Associativity associativity = operator.get().getAssociativity();
            if (associativity == Associativity.Left) {
                precedence++;
            } else if (associativity == Associativity.None) {
                // precedence = OperatorInformation.MAX_PRECEDENCE + 1;
                precedence++;
            }

            ExpressionNode rhs = parseExpression(precedence);
            lhs = new BinaryExpressionNode(
                opToken.getColumn(), opToken.getLine(), BinaryOperator.fromToken(opToken), lhs, rhs, false
            );
        }
        return lhs;
    }

    private ExpressionNode parseUnaryExpression() {
        Token token = tokenStream.get();
        switch (token.getType()) {
        case Operator_Not: {
                tokenStream.next();
                ExpressionNode expr = parseUnaryExpression();
                return new UnaryExpressionNode(
                    token.getLine(), token.getColumn(), UnaryOperator.LogicalNegation, expr, false
                );
            }
        case Operator_Minus: {
                tokenStream.next();
                ExpressionNode expr = parseUnaryExpression();
                return new UnaryExpressionNode(
                    token.getLine(), token.getColumn(), UnaryOperator.ArithmeticNegation, expr, false
                );
            }
        default: {
                return parsePostfixExpression();
            }
        }
    }

    private ExpressionNode parsePostfixExpression() {
        ExpressionNode current = parsePrimaryExpression();

        while (tokenStream.get().getType() == Operator_Dot || tokenStream.get().getType() == Operator_BracketL) {
            Token token = tokenStream.get();
            switch (token.getType()) {
                case Operator_Dot:
                    current = parseMethodInvocationOrFieldAccess(current);
                    break;
                case Operator_BracketL:
                    current = parseArrayAccess(current);
                    break;
                default:
                    // never reached, see while condition
                    throw new ParseException(token, "expected dot or array access");
            }
        }
        return current;
    }

    private ExpressionNode parseMethodInvocationOrFieldAccess(ExpressionNode object) {
        expect(Operator_Dot);
        Token name = expect(Identifier);

        if (tokenStream.get(0).getType() == Operator_ParenL) {
            // method access
            tokenStream.next();
            List<ExpressionNode> args = parseArguments();
            expect(Operator_ParenR);
            return new MethodInvocationExpressionNode(
                name.getLine(), name.getColumn(), Optional.of(object), name.getIntValue().get(), args, false
            );
        } // else field access
        return new FieldAccessExpressionNode(
            name.getLine(), name.getColumn(), object, name.getIntValue().get(), false
        );
    }

    private ArrayAccessExpressionNode parseArrayAccess(ExpressionNode object) {
        expect(Operator_BracketL);
        ExpressionNode expr = parseExpression();
        expect(Operator_BracketR);
        return new ArrayAccessExpressionNode(object.getLine(), object.getColumn(), object, expr, false);
    }

    private List<ExpressionNode> parseArguments() {
        List<ExpressionNode> args = new ArrayList<>();
        if (tokenStream.get().getType() == TokenType.Operator_ParenR) {
            // empty arguments
            return args;
        }

        args.add(parseExpression());
        while (tokenStream.get().getType() == TokenType.Operator_Comma) {
            tokenStream.next();
            args.add(parseExpression());
        }
        return args;
    }

    // helper functions
    private boolean check(TokenType... types) {
        for (int i = 0; i < types.length; i++) {
            if (tokenStream.get(i).getType() != types[i]) {
                return false;
            }
        }

        return true;
    }

    private Token expect(TokenType type) {
        Token token = tokenStream.get();
        if (token.getType() != type) {
            throw new ParseException(tokenStream.get(), "expected " + type.name());
        }

        tokenStream.next();
        return token;
    }
}
