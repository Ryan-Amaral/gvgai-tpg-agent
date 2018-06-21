package agent;

// https://stackoverflow.com/questions/702415/how-to-know-if-other-threads-have-finished
public interface ThreadCompleteListener {
    void notifyOfThreadComplete(final Thread thread);
}
