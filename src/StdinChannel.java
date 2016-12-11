import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;

public class StdinChannel extends SelectableChannel {
    private int iValidOps;

    public StdinChannel() {
        iValidOps = 0;

    }

    @Override
    protected void implCloseChannel() throws IOException {

    }

    @Override
    public SelectorProvider provider() {
        return null;
    }

    @Override
    public int validOps() {
        return iValidOps;
    }

    @Override
    public boolean isRegistered() {
        return false;
    }

    @Override
    public SelectionKey keyFor(Selector sel) {
        return null;
    }

    @Override
    public SelectionKey register(Selector sel, int ops, Object att) throws ClosedChannelException {
        return null;
    }

    @Override
    public SelectableChannel configureBlocking(boolean block) throws IOException {
        if (block)
            throw new IOException("This channel does not support blocking mode.");
        return this;
    }

    @Override
    public boolean isBlocking() {
        return false;
    }

    @Override
    public Object blockingLock() {
        return null;
    }
}
