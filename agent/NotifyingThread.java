package agent;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

// https://stackoverflow.com/questions/702415/how-to-know-if-other-threads-have-finished
public abstract class NotifyingThread extends Thread {
    private final Set<ThreadCompleteListener> listeners = new CopyOnWriteArraySet<ThreadCompleteListener>();
    public boolean plzKillMe;
    public final void addListener(final ThreadCompleteListener listener) {
        listeners.add(listener);
    }

    public final void removeListener(final ThreadCompleteListener listener) {
        listeners.remove(listener);
    }

    private final void notifyListeners() {
        for (ThreadCompleteListener listener : listeners) {
            listener.notifyOfThreadComplete(this);
        }
    }

    @Override
    public final void run() {
        try {
            doRun();
        } finally {
            if(!plzKillMe) {
                notifyListeners();
            }
        }
    }

    public abstract void doRun();
}