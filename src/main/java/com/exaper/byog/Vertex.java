package com.exaper.byog;

import android.text.TextUtils;

public class Vertex {
    private final String mId;

    public Vertex(String id) {
        this.mId = id;
    }

    public String getId() {
        return mId;
    }

    @Override
    public int hashCode() {
        return mId.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof Vertex && TextUtils.equals(((Vertex) obj).mId, mId));
    }
}
