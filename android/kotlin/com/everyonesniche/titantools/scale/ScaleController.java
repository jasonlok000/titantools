package com.everyonesniche.titantools.scale;

/** Density transaction independent of Android UI, allowing real control-flow unit tests. */
public final class ScaleController {
    public interface Commands { String run(String... args) throws Exception; }
    public interface Store { int lastWidth(); void original(int density) throws Exception; void save(int width, int density) throws Exception; }
    public interface Cancellation { void check() throws Exception; }
    public interface Progress { void stage(String value); }
    public static final class Failure extends Exception {
        public final String code;
        public Failure(String code, Throwable e) { super(e.getMessage(),e); this.code=code; }
    }
    private final Commands commands;
    private final Store store;
    private final Cancellation cancel;
    private final Progress progress;
    private final ScaleMath.Presets presets;
    public ScaleController(Commands commands, Store store, Cancellation cancel, Progress progress) {
        this(commands,store,cancel,progress,ScaleMath.Presets.DEFAULTS);
    }
    public ScaleController(Commands commands, Store store, Cancellation cancel, Progress progress, ScaleMath.Presets presets) {
        this.commands=commands; this.store=store; this.cancel=cancel; this.progress=progress; this.presets=presets;
    }
    public int toggle() throws Failure {
        String stage="READ_SIZE";
        int previous=0;
        boolean previousOverride=false, applied=false;
        try {
            progress.stage(stage); cancel.check();
            int[] size=WmOutput.size(commands.run("size"));
            stage="READ_DENSITY"; progress.stage(stage); cancel.check();
            String before=commands.run("density");
            previous=WmOutput.density(before); previousOverride=WmOutput.hasOverride(before);
            int width=ScaleMath.widthDp(size[0],size[1],previous);
            int target=ScaleMath.next(store.lastWidth(),width,previous,presets);
            int dpi=ScaleMath.density(size[0],size[1],target,presets);
            stage="SAVE_ORIGINAL"; progress.stage(stage); cancel.check(); store.original(previous);
            stage="APPLY"; progress.stage(stage); cancel.check();
            commands.run("density",Integer.toString(dpi));
            applied=true;
            stage="VERIFY"; progress.stage(stage); cancel.check();
            int actual=WmOutput.density(commands.run("density"));
            if (actual != dpi) throw new IllegalStateException("Expected "+dpi+" DPI; got "+actual);
            stage="SAVE_STATE"; progress.stage(stage); cancel.check();
            store.save(target,dpi);
            return target;
        } catch (Exception e) {
            // Do not blindly reset display defaults. Restore the preceding override only
            // after a successful apply that failed verification (not after denied/timeout calls).
            if (applied && "VERIFY".equals(stage)) {
                try { cancel.check(); commands.run("density",previousOverride ? Integer.toString(previous) : "reset"); }
                catch (Exception rollback) { e.addSuppressed(rollback); }
            }
            throw new Failure(stage,e);
        }
    }
}
