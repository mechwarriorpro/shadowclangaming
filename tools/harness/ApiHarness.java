import com.shadowclangaming.computer.api.Component;
import com.shadowclangaming.computer.api.ComputerApi;
import com.shadowclangaming.computer.api.EventQueue;
import com.shadowclangaming.computer.api.Machine;
import com.shadowclangaming.computer.api.MemoryFileSystem;
import com.shadowclangaming.computer.api.ScreenComponent;
import com.shadowclangaming.computer.api.TextBuffer;
import com.shadowclangaming.lua.Lua;
import com.shadowclangaming.lua.LuaError;
import com.shadowclangaming.lua.Vm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs real Lua programs against the computer API (screen, term, fs, component, event, thread) and checks
 * what they drew, wrote and read.
 *
 * Nothing is mocked except the machine itself: this is the same ComputerApi the block entity installs.
 */
public final class ApiHarness {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        textBuffer();
        screenAndGpu();
        terminal();
        filesystem();
        components();
        events();
        threads();
        aSmallProgram();

        System.out.println();
        System.out.println(failures == 0
                ? "ALL API TESTS PASS (" + checks + " assertions)"
                : failures + " FAILURE(S) of " + checks + " assertions");
        if (failures != 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ harness plumbing

    /** A machine with no world behind it: one screen and an event queue. */
    static final class FakeMachine implements Machine {
        final List<Component> components = new ArrayList<>();
        final EventQueue events = new EventQueue();
        final List<String> log = new ArrayList<>();
        long uptime;

        @Override
        public List<Component> components() {
            return components;
        }

        @Override
        public EventQueue events() {
            return events;
        }

        @Override
        public long uptimeTicks() {
            return uptime;
        }

        @Override
        public String address() {
            return "test0";
        }

        @Override
        public void log(String line) {
            log.add(line);
        }
    }

    /** One test environment: machine + screen + api + vm. */
    static final class Session {
        final FakeMachine machine = new FakeMachine();
        final ScreenComponent screen = new ScreenComponent("screen0", 40, 10);
        final MemoryFileSystem fs = new MemoryFileSystem(64L * 1024L, "test");
        final ComputerApi api;
        final Vm vm;
        final List<String> out = new ArrayList<>();

        Session() {
            machine.components.add(screen);
            api = new ComputerApi(machine, fs);
            vm = Lua.newVm();
            api.install(vm);
            vm.out = out::add;
        }

        void run(String source) {
            Lua.run(source, vm);
        }
    }

    private static void textBuffer() {
        TextBuffer buffer = new TextBuffer(10, 4);
        buffer.set(1, 1, "hello", false);
        eq("buffer stores text", "hello     ", buffer.line(1));
        eq("buffer clips off the edge", "hell", buffer.line(1).substring(0, 4));

        buffer.set(10, 2, "xyz", false);
        eq("writing past the right edge is clipped", "         x", buffer.line(2));

        buffer.set(1, 2, "abcdef", true);
        eq("vertical writes go down", "a", buffer.line(2).substring(0, 1) + "");

        buffer.fill(1, 3, 4, 1, '#');
        eq("fill", "####      ", buffer.line(3));

        buffer.scroll(1);
        eq("scrolling up copies row 3 to row 2", "####      ", buffer.line(2));
        eq("the bottom row is blank after scrolling", "          ", buffer.line(4));

        buffer.clear();
        check("clear blanks every cell", buffer.blank());

        TextBuffer coloured = new TextBuffer(5, 2);
        coloured.setForeground(0xFF0000);
        coloured.set(1, 1, "red", false);
        check("cells remember their foreground", coloured.foregroundAt(1, 1) == 0xFF0000);
        check("unwritten cells keep the old colour", coloured.foregroundAt(5, 2) != 0xFF0000);
    }

    private static void screenAndGpu() {
        Session session = new Session();
        session.run("""
                local w, h = gpu.getResolution()
                print(w, h)
                gpu.setForeground(0x00FF00)
                gpu.set(1, 1, 'VOLTZ')
                print(gpu.getForeground())
                print(gpu.get(1, 1))
                print(gpu.getScreen())
                """);
        eq("gpu.getResolution", "40\t10", line(session.out, 0));
        eq("gpu.getForeground echoes the colour", "0x00FF00", line(session.out, 1));
        eq("gpu.get reads a cell back", "V", line(session.out, 2));
        eq("gpu.getScreen names the screen", "screen0", line(session.out, 3));
        eq("gpu.set drew to the screen", "VOLTZ", session.screen.buffer().line(1).trim());
        check("gpu.set used the script's colour", session.screen.buffer().foregroundAt(1, 1) == 0x00FF00);
    }

    private static void terminal() {
        Session session = new Session();
        session.run("""
                term.write('MineOS-ish shell\\n')
                term.write('ready ')
                term.write('> ')
                local x, y = term.getCursor()
                print(x, y)
                term.setCursor(3, 3)
                term.write('mid')
                print(gpu.get(3, 3))
                term.setCursor(1, 3)
                term.clearLine()
                print(term.getSize())
                """);
        eq("term writes on the first row", "MineOS-ish shell", session.screen.buffer().line(1).trim());
        eq("term advances across a row", "ready >", session.screen.buffer().line(2).trim());
        eq("cursor is reported 1-based", "9\t2", line(session.out, 0));
        eq("writing at a cursor position lands in the right cell", "m", line(session.out, 1));
        eq("clearLine from column 1 wipes the row", "", session.screen.buffer().line(3).trim());
        eq("term.getSize", "40\t10", line(session.out, 2));

        // Wrapping and scrolling: 40 columns wide, 10 rows high, write 45 characters.
        Session wrap = new Session();
        wrap.run("""
                for i = 1, 12 do term.write(string.rep('x', 40)) end
                """);
        eq("writing 40 characters wraps to the next row", 40, wrap.screen.buffer().line(10).trim().length());
        check("the screen scrolled instead of overflowing",
                wrap.screen.buffer().line(9).indexOf('x') == 0);
    }

    private static void filesystem() {
        Session session = new Session();
        session.run("""
                fs.makeDirectory('/etc')
                local f = fs.open('/etc/motd', 'w')
                fs.write(f, 'hello disk')
                fs.close(f)
                local g = fs.open('/etc/motd', 'r')
                print(fs.read(g, 5))
                print(fs.read(g))
                fs.close(g)
                print(fs.exists('/etc/motd'), fs.size('/etc/motd'))
                local names = {}
                for name in fs.list('/etc') do names[#names + 1] = name end
                print(names[1])
                print(fs.exists('/../secret'), fs.exists('/etc/../etc/motd'))
                """);
        eq("fs.read honours a count", "hello", line(session.out, 0));
        eq("fs.read continues from the cursor", " disk", line(session.out, 1));
        eq("fs.exists and size", "true\t10", line(session.out, 2));
        eq("fs.list iterates a directory", "motd", line(session.out, 3));
        eq("paths cannot escape the root", "false\ttrue", line(session.out, 4));

        check("the disk really holds the file", "hello disk".equals(session.fs.read("/etc/motd")));
        check("delete removes it", session.fs.delete("/etc/motd"));
        check("the file is gone", !session.fs.exists("/etc/motd"));
    }

    private static void components() {
        Session session = new Session();
        session.run("""
                local list = component.list()
                local n = 0
                for address, kind in pairs(list) do n = n + 1 end
                print(n >= 2)
                print(component.type('screen0'))
                local screen = component.get('screen0')
                screen.setText(2, 2, 'direct')
                print(component.invoke('screen0', 'getText', 2, 2))
                print(component.get('nope') == nil)
                """);
        eq("component.list finds the screen and the disk", "true", line(session.out, 0));
        eq("component.type", "screen", line(session.out, 1));
        eq("a component proxy calls through", "d", line(session.out, 2));
        eq("missing components are nil, not an error", "true", line(session.out, 3));
        eq("the proxy drew on the real screen", "direct", session.screen.buffer().line(2).trim());
    }

    private static void events() {
        Session session = new Session();
        session.run("""
                event.push('redstone', 'left', 15)
                local name, side, power = event.pull('redstone')
                print(name, side, power)
                """);
        eq("a queued event is delivered immediately", "redstone\tleft\t15", line(session.out, 0));

        // An empty queue means the script suspends, and the machine resumes it later.
        Session waiting = new Session();
        try {
            waiting.run("local n, v = event.pull('key_down')\nprint('got ' .. v)");
            check("event.pull suspends when the queue is empty", false);
        } catch (Vm.Yield yield) {
            check("event.pull suspends when the queue is empty", "key_down".equals(yield.waitingFor));
        }
        waiting.vm.resumeWith("key_down", "enter");
        waiting.vm.run(100_000);
        eq("the suspended script resumed with its event", "got enter", line(waiting.out, 0));

        Session timers = new Session();
        timers.run("event.timer(2)");
        eq("a timer is pending, not queued", 1, timers.api.pendingTimers());
        timers.machine.uptime = 39;
        timers.api.tickTimers();
        check("the timer has not fired early", timers.machine.events.isEmpty());
        timers.machine.uptime = 40;
        timers.api.tickTimers();
        eq("the timer fires on its tick", "timer", timers.machine.events.names().get(0));

        // event.listen handlers run before the event is queued.
        Session listening = new Session();
        listening.run("""
                event.listen('ping', function(name, v) print('heard ' .. name .. ':' .. v) end)
                """);
        listening.api.dispatch(new EventQueue.Event("ping", new Object[]{"x"}));
        eq("a listener ran with the event name and its values", "heard ping:x", line(listening.out, 0));
        eq("the event was still queued", 1, listening.machine.events.size());
    }

    private static void threads() {
        Session session = new Session();
        session.run("""
                local total = {n = 0}
                local a = thread.create(function() total.n = total.n + 1 end)
                local b = thread.create(function() total.n = total.n + 10 end)
                thread.waitForAll(a, b)
                print(total.n)
                """);
        eq("thread.waitForAll runs both threads", "11", line(session.out, 0));
    }

    private static void aSmallProgram() {
        // Everything at once: a filesystem-backed login banner drawn on the screen from a coroutine.
        Session session = new Session();
        session.fs.write("/etc/motd", "ShadowClanGaming");
        session.run("""
                local function line(y, text)
                  term.setCursor(1, y)
                  term.clearLine()
                  term.write(text)
                end
                local file = fs.open('/etc/motd', 'r')
                local banner = fs.read(file)
                fs.close(file)
                line(1, '== ' .. banner .. ' ==')
                line(3, string.format('%d cores, %s', 4, 'online'))
                gpu.setForeground(0x00AAFF)
                gpu.set(1, 5, 'radar: ' .. string.format('%.1f', 12.34) .. ' km')
                local drew = 0
                local t = thread.create(function()
                  for i = 1, 3 do drew = drew + 1 end
                end)
                thread.waitForAll(t)
                print(drew)
                print(('x'):rep(3))
                """);
        eq("the banner came from the disk", "== ShadowClanGaming ==", session.screen.buffer().line(1).trim());
        eq("format works through the API", "4 cores, online", session.screen.buffer().line(3).trim());
        eq("gpu.set is exact", "radar: 12.3 km", session.screen.buffer().line(5).trim());
        eq("threads ran to completion", "3", line(session.out, 0));
        eq("string methods still work", "xxx", line(session.out, 1));
    }

    // ------------------------------------------------------------------ plumbing

    private static String line(List<String> lines, int index) {
        return index < lines.size() ? lines.get(index) : "<missing line " + index + ">";
    }

    private static void eq(String label, String expected, String actual) {
        checks++;
        if (expected.equals(actual)) {
            System.out.println("ok   " + label);
        } else {
            failures++;
            System.out.println("FAIL " + label + ": expected '" + expected + "' but was '" + actual + "'");
        }
    }

    private static void eq(String label, int expected, int actual) {
        eq(label, Integer.toString(expected), Integer.toString(actual));
    }

    private static void check(String label, boolean condition) {
        checks++;
        if (condition) {
            System.out.println("ok   " + label);
        } else {
            failures++;
            System.out.println("FAIL " + label);
        }
    }
}
