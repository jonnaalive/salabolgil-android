package kr.salabolgil.app.core;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;

/** Atomic app-private persistence, excluded from Android backup by the application manifest. */
public final class StateStore {
    private final AtomicFile file;
    public StateStore(Context context) { file = new AtomicFile(new File(context.getFilesDir(), "trial-state-v1.bin")); }
    public AppData load() throws IOException {
        if (!file.getBaseFile().exists()) return AppData.fresh(true);
        try (ObjectInputStream in = new ObjectInputStream(file.openRead())) {
            Object value = in.readObject();
            if (!(value instanceof AppData) || ((AppData)value).schemaVersion != 1) throw new IOException("저장 형식이 지원되지 않습니다.");
            return (AppData) value;
        } catch (ClassNotFoundException | RuntimeException e) { throw new IOException("저장한 기록을 읽을 수 없습니다.", e); }
    }
    public void save(AppData state) throws IOException {
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            ObjectOutputStream out = new ObjectOutputStream(stream);
            out.writeObject(state); out.flush(); file.finishWrite(stream);
        } catch (IOException e) { if (stream != null) file.failWrite(stream); throw e; }
    }
    public void delete() { file.delete(); }
}
