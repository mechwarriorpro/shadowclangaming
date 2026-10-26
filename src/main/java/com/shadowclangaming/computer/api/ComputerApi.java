package com.shadowclangaming.computer.api;

import com.shadowclangaming.lua.LuaCoroutine;
import com.shadowclangaming.lua.LuaError;
import com.shadowclangaming.lua.LuaFunction;
import com.shadowclangaming.lua.LuaTable;
import com.shadowclangaming.lua.Vm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The machine's script environment: {@code component}, {@code computer}, {@code event}, {@code thread},
 * {@code gpu}, {@code term}, {@code fs} and {@code colors}.
 *
 * The shape is the one MineOS and the wider ecosystem of programs is written against, because the whole
 * point of this round is to run that software rather than a lookalike that nothing runs on. Where OpenOS's
 * behaviour is load-bearing ({@code event.pull} suspending, {@code fs.open} returning {@code nil, err},
 * {@code component.get} accepting an address prefix) it is matched exactly; where it is not, the simpler
 * behaviour is documented in docs/LUA.md.
 *
 * Note what is *not* here: no timer threads, no sleeping, no direct world access. A script that waits throws
 * {@link com.shadowclangaming.lua.Vm.Yield} and the block entity resumes it - which is the only way this can be
 * safe on a server ticking 20 times a second.
 */
public final class ComputerApi {

    /** What {@code computer.shutdown} asked for. */
    public enum PowerRequest {
        NONE,
        SHUTDOWN,
        REBOOT
    }

    private final Machine machine;
    private final ComponentRegistry registry = new ComponentRegistry();
    private final GpuLibrary gpu;
    private final TermLibrary term;
    private final FileSystem filesystem;
    private final FileHandles handles;
    private final Map<String, LuaFunction> listeners = new LinkedHashMap<>();
    private final List<LuaCoroutine> threads = new ArrayList<>();
    private final List<long[]> timers = new ArrayList<>();

    private PowerRequest powerRequest = PowerRequest.NONE;
    private Vm vm;

    public ComputerApi(Machine machine, FileSystem filesystem) {
        this.machine = machine;
        this.filesystem = filesystem;
        this.gpu = new GpuLibrary(registry);
        this.term = new TermLibrary(gpu);
        this.handles = new FileHandles(filesystem);
    }

    public ComponentRegistry registry() {
        return registry;
    }

    public GpuLibrary gpu() {
        return gpu;
    }

    public TermLibrary term() {
        return term;
    }

    public FileSystem filesystem() {
        return filesystem;
    }

    public PowerRequest powerRequest() {
        return powerRequest;
    }

    public void clearPowerRequest() {
        powerRequest = PowerRequest.NONE;
    }

    /** Re-read the components the machine can see. Cheap, called at most once a tick. */
    public void refresh() {
        registry.clear();
        for (Component component : machine.components()) {
            registry.register(component);
        }
        registry.register(new DiskComponent(machine.address() + "-disk", filesystem, handles));
        if (!gpu.bound()) {
            gpu.bindPrimary();
        }
    }

    /** Install every library into a freshly created machine. */
    public void install(Vm vm) {
        this.vm = vm;
        refresh();
        LuaTable globals = vm.globals;
        installColors(globals);
        gpu.install(vm, globals);
        term.install(vm, globals);
        installComponent(globals);
        installComputer(globals);
        installEvent(globals);
        installThread(globals);
        installFs(globals);
    }

    // ------------------------------------------------------------------ libraries

    private void installColors(LuaTable globals) {
        LuaTable colors = new LuaTable();
        String[] names = {"white", "orange", "magenta", "lightBlue", "yellow", "lime", "pink", "gray",
                "lightGray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        for (int i = 0; i < names.length; i++) {
            colors.set(names[i], (double) i);
        }
        colors.set("lightblue", 3.0D);
        globals.set("colors", colors);
    }

    private void installComponent(LuaTable globals) {
        LuaTable component = new LuaTable();
        globals.set("component", component);
        native_(component, "list", (machine1, args) -> {
            String filter = args.length > 0 && args[0] != null ? ScreenComponent.stringOf(args[0]) : null;
            LuaTable out = new LuaTable();
            for (Component entry : registry.all()) {
                if (filter != null && !entry.type().startsWith(filter)) {
                    continue;
                }
                out.set(entry.address(), entry.type());
            }
            return new Object[]{out};
        });
        native_(component, "get", (machine1, args) -> {
            Component entry = registry.get(args.length > 0 && args[0] != null
                    ? ScreenComponent.stringOf(args[0]) : null);
            if (entry == null) {
                return new Object[]{null};
            }
            return new Object[]{proxy(entry)};
        });
        native_(component, "type", (machine1, args) -> {
            Component entry = registry.get(args.length > 0 && args[0] != null
                    ? ScreenComponent.stringOf(args[0]) : null);
            return new Object[]{entry == null ? null : entry.type()};
        });
        native_(component, "methods", (machine1, args) -> {
            Component entry = registry.get(args.length > 0 && args[0] != null
                    ? ScreenComponent.stringOf(args[0]) : null);
            if (entry == null) {
                return new Object[]{null};
            }
            String[] names = entry.methods();
            Object[] out = new Object[names.length];
            System.arraycopy(names, 0, out, 0, names.length);
            return out;
        });
        native_(component, "invoke", (machine1, args) -> {
            Component entry = registry.get(args.length > 0 && args[0] != null
                    ? ScreenComponent.stringOf(args[0]) : null);
            if (entry == null) {
                return new Object[]{null, "no such component"};
            }
            String method = args.length > 1 && args[1] != null ? ScreenComponent.stringOf(args[1]) : "";
            Object[] rest = new Object[Math.max(0, args.length - 2)];
            System.arraycopy(args, 2, rest, 0, rest.length);
            try {
                return entry.invoke(method, rest);
            } catch (IllegalArgumentException error) {
                return new Object[]{null, error.getMessage()};
            }
        });
        native_(component, "fields", (machine1, args) -> new Object[]{null, "no fields"});
        native_(component, "doc", (machine1, args) -> new Object[]{null, "no documentation"});
    }

    /** A table whose methods forward to one component, so {@code screen.setText(...)} works. */
    private LuaTable proxy(Component entry) {
        LuaTable table = new LuaTable();
        table.set("address", entry.address());
        table.set("type", entry.type());
        for (String name : entry.methods()) {
            native_(table, name, (machine1, args) -> {
                try {
                    return entry.invoke(name, args);
                } catch (IllegalArgumentException error) {
                    throw new LuaError(error.getMessage());
                }
            });
        }
        return table;
    }

    private void installComputer(LuaTable globals) {
        LuaTable computer = new LuaTable();
        globals.set("computer", computer);
        native_(computer, "address", (machine1, args) -> new Object[]{machine.address()});
        native_(computer, "uptime", (machine1, args) -> new Object[]{machine.uptimeTicks() / 20.0D});
        native_(computer, "realTime", (machine1, args) ->
                new Object[]{Math.max(0L, machine.uptimeTicks()) * 50.0D});
        native_(computer, "freeMemory", (machine1, args) -> new Object[]{(double) machine.freeMemory()});
        native_(computer, "totalMemory", (machine1, args) -> new Object[]{(double) machine.totalMemory()});
        native_(computer, "pushSignal", (machine1, args) -> {
            String name = args.length > 0 && args[0] != null ? ScreenComponent.stringOf(args[0]) : "signal";
            Object[] rest = new Object[Math.max(0, args.length - 1)];
            System.arraycopy(args, 1, rest, 0, rest.length);
            machine.events().push(name, rest);
            return new Object[]{Boolean.FALSE};
        });
        native_(computer, "pullSignal", (machine1, args) -> pullEvent(null, 0.0D));
        native_(computer, "shutdown", (machine1, args) -> {
            powerRequest = PowerRequest.SHUTDOWN;
            return new Object[0];
        });
        native_(computer, "reboot", (machine1, args) -> {
            powerRequest = PowerRequest.REBOOT;
            return new Object[0];
        });
        native_(computer, "isRunning", (machine1, args) -> new Object[]{Boolean.TRUE});
    }

    private void installEvent(LuaTable globals) {
        LuaTable event = new LuaTable();
        globals.set("event", event);
        native_(event, "push", (machine1, args) -> {
            String name = args.length > 0 && args[0] != null ? ScreenComponent.stringOf(args[0]) : "signal";
            Object[] rest = new Object[Math.max(0, args.length - 1)];
            System.arraycopy(args, 1, rest, 0, rest.length);
            machine.events().push(name, rest);
            return new Object[]{Boolean.FALSE};
        });
        native_(event, "pull", (machine1, args) -> {
            String name = args.length > 0 && args[0] != null ? ScreenComponent.stringOf(args[0]) : null;
            double timeout = args.length > 1 && args[1] instanceof Double value ? value : 0.0D;
            return pullEvent(name, timeout);
        });
        native_(event, "listen", (machine1, args) -> {
            String name = args.length > 0 && args[0] != null ? ScreenComponent.stringOf(args[0]) : null;
            Object handler = args.length > 1 ? args[1] : null;
            if (name == null || !(handler instanceof LuaFunction function)) {
                return new Object[]{null, "listen needs a name and a function"};
            }
            listeners.put(name, function);
            return new Object[]{Boolean.TRUE};
        });
        native_(event, "ignore", (machine1, args) -> {
            String name = args.length > 0 && args[0] != null ? ScreenComponent.stringOf(args[0]) : null;
            if (name == null) {
                return new Object[]{null, "ignore needs a name"};
            }
            listeners.remove(name);
            return new Object[]{Boolean.TRUE};
        });
        native_(event, "timer", (machine1, args) -> {
            // A timer is a due tick in a list, not a Java thread: the machine's ticker queues the event
            // when the tick arrives, so a thousand timers cost a thousand comparisons, not a thousand
            // threads.
            double seconds = args.length > 0 && args[0] instanceof Double value ? value : 0.0D;
            long due = machine.uptimeTicks() + Math.max(0L, (long) Math.ceil(seconds * 20.0D));
            timers.add(new long[]{due});
            return new Object[]{Boolean.TRUE};
        });
    }

    private void installThread(LuaTable globals) {
        LuaTable thread = new LuaTable();
        globals.set("thread", thread);
        native_(thread, "create", (machine1, args) -> {
            if (!(args.length > 0 && args[0] instanceof LuaFunction function)) {
                return new Object[]{null, "thread.create needs a function"};
            }
            LuaCoroutine coroutine = LuaCoroutine.create(vm, function);
            threads.add(coroutine);
            return new Object[]{coroutine};
        });
        native_(thread, "waitForAll", (machine1, args) -> {
            boolean alive = false;
            for (Object argument : args) {
                if (!(argument instanceof LuaCoroutine coroutine) || coroutine.dead()) {
                    continue;
                }
                try {
                    coroutine.resume();
                } catch (LuaError error) {
                    machine.log("thread failed: " + error.getMessage());
                    continue;
                }
                alive |= !coroutine.dead();
            }
            threads.removeIf(LuaCoroutine::dead);
            if (alive) {
                // Not finished: hand the tick back and try again on the next one.
                throw new Vm.Yield("thread", 1L);
            }
            return new Object[0];
        });
        native_(thread, "status", (machine1, args) -> {
            if (!(args.length > 0 && args[0] instanceof LuaCoroutine coroutine)) {
                return new Object[]{null, "status needs a thread"};
            }
            return new Object[]{coroutine.dead() ? "dead" : coroutine.started() ? "suspended" : "new"};
        });
    }

    private void installFs(LuaTable globals) {
        LuaTable fs = new LuaTable();
        globals.set("fs", fs);
        native_(fs, "open", (machine1, args) -> handles.open(
                text(args, 0, "/"),
                args.length > 1 ? text(args, 1, "r") : "r"));
        native_(fs, "read", (machine1, args) -> handles.read(
                text(args, 0, ""),
                args.length > 1 && args[1] instanceof Double count ? (int) (double) count : 0));
        native_(fs, "write", (machine1, args) -> handles.write(
                text(args, 0, ""),
                text(args, 1, "")));
        native_(fs, "close", (machine1, args) -> handles.close(text(args, 0, "")));
        native_(fs, "seek", (machine1, args) -> handles.seek(
                text(args, 0, ""),
                args.length > 1 ? text(args, 1, "cur") : "cur",
                args.length > 2 && args[2] instanceof Double offset ? (int) (double) offset : 0));
        native_(fs, "exists", (machine1, args) -> new Object[]{filesystem.exists(text(args, 0, "/"))});
        native_(fs, "isDirectory", (machine1, args) ->
                new Object[]{filesystem.isDirectory(text(args, 0, "/"))});
        native_(fs, "size", (machine1, args) -> new Object[]{(double) filesystem.size(text(args, 0, "/"))});
        native_(fs, "lastModified", (machine1, args) ->
                new Object[]{(double) filesystem.lastModified(text(args, 0, "/"))});
        native_(fs, "makeDirectory", (machine1, args) ->
                new Object[]{filesystem.makeDirectory(text(args, 0, "/"))});
        native_(fs, "remove", (machine1, args) -> new Object[]{filesystem.delete(text(args, 0, "/"))});
        native_(fs, "rename", (machine1, args) ->
                new Object[]{filesystem.rename(text(args, 0, "/"), text(args, 1, "/"))});
        native_(fs, "copy", (machine1, args) -> {
            String from = text(args, 0, "/");
            String to = text(args, 1, "/");
            String content = filesystem.read(from);
            if (content == null) {
                return new Object[]{null, "no such file"};
            }
            return new Object[]{filesystem.write(to, content) ? Boolean.TRUE : null};
        });
        native_(fs, "getLabel", (machine1, args) -> new Object[]{filesystem.label()});
        native_(fs, "setLabel", (machine1, args) -> {
            filesystem.setLabel(text(args, 0, ""));
            return new Object[0];
        });
        native_(fs, "space", (machine1, args) -> {
            long capacity = filesystem instanceof MemoryFileSystem memory ? memory.capacity() : 1_000_000L;
            long used = filesystem instanceof MemoryFileSystem memory ? memory.used() : filesystem.size("/");
            return new Object[]{(double) capacity, (double) used};
        });
        native_(fs, "list", (machine1, args) -> {
            // An iterator, because `for name in fs.list(dir) do` is how every program walks a directory.
            String path = text(args, 0, "/");
            List<String> names = filesystem.list(path);
            LuaTable state = new LuaTable();
            for (int i = 0; i < names.size(); i++) {
                state.set((double) (i + 1), names.get(i));
            }
            int[] position = {0};
            return new Object[]{new LuaFunction.NativeFunction("fs_list_iter", (machine2, iteratorArgs) -> {
                // Returns the next name, one value, because every program writes
                // `for name in fs.list(dir) do`. The generic-for hands the last name back as its control
                // value, so the position lives in Java instead.
                position[0]++;
                Object value = state.get((double) position[0]);
                return value == null ? new Object[0] : new Object[]{value};
            }), state, null};
        });
    }

    // ------------------------------------------------------------------ events

    /**
     * {@code event.pull}: take a matching event, or suspend.
     *
     * A queued event is returned immediately. Otherwise the machine is asked to wake the script when the
     * event arrives (or when the timeout expires, in which case the script gets nothing back and can tell
     * the difference by the nil).
     */
    private Object[] pullEvent(String name, double timeoutSeconds) {
        EventQueue.Event event = machine.events().take(name);
        if (event != null) {
            return withName(event);
        }
        throw new Vm.Yield(name == null ? "event" : name,
                timeoutSeconds <= 0.0D ? 0L : (long) Math.ceil(timeoutSeconds * 20.0D));
    }

    private static Object[] withName(EventQueue.Event event) {
        Object[] out = new Object[event.args().length + 1];
        out[0] = event.name();
        System.arraycopy(event.args(), 0, out, 1, event.args().length);
        return out;
    }

    /**
     * Run the registered {@code event.listen} handlers for an event, then queue it.
     *
     * Handlers run first so a program that both listens and pulls sees the same order OpenComputers did.
     */
    public void dispatch(EventQueue.Event event) {
        LuaFunction handler = listeners.get(event.name());
        if (handler != null && vm != null) {
            try {
                vm.call(handler, withName(event));
            } catch (LuaError error) {
                machine.log("listener error: " + error.getMessage());
            }
        }
        machine.events().push(event.name(), event.args());
    }

    private static String text(Object[] args, int index, String fallback) {
        if (index >= args.length || args[index] == null) {
            return fallback;
        }
        return ScreenComponent.stringOf(args[index]);
    }

    /** Queue the timer events that have come due. Called once per tick by the block entity. */
    public void tickTimers() {
        long now = machine.uptimeTicks();
        for (int i = timers.size() - 1; i >= 0; i--) {
            if (timers.get(i)[0] <= now) {
                timers.remove(i);
                machine.events().push("timer");
            }
        }
    }

    public int pendingTimers() {
        return timers.size();
    }

    private static void native_(LuaTable target, String name, LuaFunction.Native body) {
        target.set(name, new LuaFunction.NativeFunction(name, body));
    }
}
