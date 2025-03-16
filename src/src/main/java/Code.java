import java.util.Random;

public class Code {
    public static final int CODE_LENGTH = 4;
    public static char[] COLORS = new char[]{'B','G','O','R','W','Y'};
    private final  char[] codeWord = new char[CODE_LENGTH];

    public Code(Random random){
        for (int i = 0; i < CODE_LENGTH; i++) {
            codeWord[i] = COLORS[random.nextInt(COLORS.length)];
        }
    }

    public Code(String codeString){
        assert(codeString.length() == CODE_LENGTH);
        for(int i=0; i<CODE_LENGTH; i++)
            codeWord[i] = codeString.charAt(i);
    }

    @Override
    public String toString() {
        return new String(codeWord);
    }

    /**
     * return the number of colors of guess that are correctly positioned
     */
    public int numberOfColorsWithCorrectPosition(Code guess){
        int count = 0;
        for(char color : COLORS){
            count += numberOfMatches(color, guess);
        }
        return count;
    }

    /**
     * return the number of colors of guess that are in this codeWord
     * but do not have the correct position
     */
    public int numberOfColorsWithIncorrectPosition(Code guess){
        int count = 0;
        for(char color:COLORS){
            int nMatchedOccurrences = numberOfMatches(color, guess);
            int nUnmatchedOccurrencesCode  = numberOfOccurrences(color, this) - nMatchedOccurrences;
            int nUnmatchedOccurrencesGuess = numberOfOccurrences(color, guess) - nMatchedOccurrences;
            count += Math.min(nUnmatchedOccurrencesCode, nUnmatchedOccurrencesGuess);
        }
        return count;
    }


    private int numberOfOccurrences(char color, Code code){
        int count = 0;
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (code.codeWord[i] == color) count++;
        }
        return count;
    }

    private int numberOfMatches(char color, Code guess){
        int count = 0;
        for (int i = 0; i < CODE_LENGTH; i++) {
            if ((this.codeWord[i] == guess.codeWord[i]) && (this.codeWord[i] == color)){
                count++;
            }
        }
        return count;
    }

    public char[] getCodeWord() {
        return codeWord;
    }

    @Override
    public boolean equals(Object o){
        if (o == null) return false;
        if (!(o instanceof Code)) return false;
        return this.toString().equals(o.toString());
    }
}
