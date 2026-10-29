import com.abrar.raha.RahaSpeechVocabulary;

/** Vocabulary export must not depend on the retired rule-based intent teacher. */
class SpeechVocabularyExport {
    public static void main(String[] args) {
        for (String phrase : RahaSpeechVocabulary.INSTANCE.phrases()) System.out.println(phrase);
    }
}
