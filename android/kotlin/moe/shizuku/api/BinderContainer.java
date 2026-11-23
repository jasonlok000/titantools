package moe.shizuku.api;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

/** Shizuku's wire-format parcelable. The class name is part of its public protocol. */
public final class BinderContainer implements Parcelable {
    public final IBinder binder;
    public BinderContainer(IBinder binder) { this.binder = binder; }
    private BinderContainer(Parcel parcel) { binder = parcel.readStrongBinder(); }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel out, int flags) { out.writeStrongBinder(binder); }
    public static final Parcelable.Creator<BinderContainer> CREATOR = new Parcelable.Creator<BinderContainer>() {
        @Override public BinderContainer createFromParcel(Parcel in) { return new BinderContainer(in); }
        @Override public BinderContainer[] newArray(int size) { return new BinderContainer[size]; }
    };
}
