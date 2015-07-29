/*
 * Copyright (c) 2013 Patrick Scheibe
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package de.halirutan.mathematica.parsing.prattparser.parselets;

import com.intellij.lang.PsiBuilder;
import de.halirutan.mathematica.parsing.MathematicaElementTypes;
import de.halirutan.mathematica.parsing.ParserBundle;
import de.halirutan.mathematica.parsing.prattparser.CriticalParserError;
import de.halirutan.mathematica.parsing.prattparser.MathematicaParser;

/**
 * Parselet for MessageName's like symbol::usage or Sin::tag. There are some specialties about this because the left
 * operand is required to be a symbol. The right operand can be a symbol or a string.
 *
 * @author patrick (3/27/13)
 */
public class MessageNameParselet implements InfixParselet {

  private final int myPrecedence;

  public MessageNameParselet(int precedence) {
    this.myPrecedence = precedence;
  }

  @Override
  public MathematicaParser.Result parse(MathematicaParser parser, MathematicaParser.Result left) throws CriticalParserError {

    PsiBuilder.Marker messageNameMarker = left.getMark().precede();
    parser.advanceLexer();
    MathematicaParser.Result result = parser.parseExpression(myPrecedence);

    if (result.isParsed()) {
      // Check whether we have a symbol or a string in usage message
      if ((!result.getToken().equals(MathematicaElementTypes.SYMBOL_EXPRESSION)) &&
          (!result.getToken().equals(MathematicaElementTypes.STRING_LITERAL_EXPRESSION))) {
        PsiBuilder.Marker errorMark = result.getMark().precede();
        errorMark.error(ParserBundle.message("MessageName.no.symbol.or.string"));
      } else if (result.getToken().equals(MathematicaElementTypes.SYMBOL_EXPRESSION)) {
        final PsiBuilder.Marker mark = result.getMark();
        final PsiBuilder.Marker precede = mark.precede();
        precede.done(MathematicaElementTypes.STRINGIFIED_SYMBOL_EXPRESSION);
        mark.drop();
      }

      // Check whether we have the form symbol::name::language
      if (parser.matchesToken(MathematicaElementTypes.DOUBLE_COLON)) {
        parser.advanceLexer();
        result = parser.parseExpression(myPrecedence);
        if (result.isParsed() && ((!result.getToken().equals(MathematicaElementTypes.SYMBOL_EXPRESSION)) &&
            (!result.getToken().equals(MathematicaElementTypes.STRING_LITERAL_EXPRESSION)))) {
          PsiBuilder.Marker errMark = result.getMark().precede();
          errMark.error(ParserBundle.message("MessageName.no.symbol.or.string"));
        } else if (result.isValid() && result.getToken().equals(MathematicaElementTypes.SYMBOL_EXPRESSION)) {
          final PsiBuilder.Marker mark = result.getMark();
          final PsiBuilder.Marker precede = mark.precede();
          precede.done(MathematicaElementTypes.STRINGIFIED_SYMBOL_EXPRESSION);
          mark.drop();
        }
      }
    } else {
      parser.error(ParserBundle.message("MessageName.arg"));
    }
    messageNameMarker.done(MathematicaElementTypes.MESSAGE_NAME_EXPRESSION);
    return MathematicaParser.result(messageNameMarker, MathematicaElementTypes.MESSAGE_NAME_EXPRESSION, result.isParsed());

  }

  @Override
  public int getMyPrecedence() {
    return myPrecedence;
  }
}
