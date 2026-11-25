import com.shadowclangaming.lua.Lua;
import com.shadowclangaming.lua.LuaCoroutine;
import com.shadowclangaming.lua.LuaError;
import com.shadowclangaming.lua.LuaFunction;
import com.shadowclangaming.lua.Vm;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs real Lua programs through the interpreter and checks what they printed.
 *
 * This is the evidence that the Lua layer works: nothing here is mocked, the source is compiled and
 * executed by the same classes the computer uses in game, and the assertions are on the output a player
 * would see.
 *
 * Run with: bash tools/run_lua_harness.sh
 */
public final class LuaHarness {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        basics();
        moreLanguage();
        control();
        functions();
        tables();
        strings();
        errors();
        coroutines();
        budgets();

        System.out.println();
        System.out.println(failures == 0
                ? "ALL LUA TESTS PASS (" + checks + " assertions)"
                : failures + " FAILURE(S) of " + checks + " assertions");
        if (failures != 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ cases

    private static void basics() {
        expect("arithmetic and precedence",
                "print(1 + 2 * 3)\nprint((1 + 2) * 3)\nprint(2 ^ 3 ^ 2)\nprint(7 % 3)\n"
                        + "print(10 / 4)\nprint(-3 + 1)\nprint(2 * 3.5)",
                "7", "9", "512", "1", "2.5", "-2", "7");

        expect("comparison and logic",
                "print(1 < 2, 2 <= 2, 3 > 4)\nprint('a' .. 'b' .. 'c')\n"
                        + "print(nil == false)\nprint(true and 'yes' or 'no')\nprint(not nil)",
                "true\ttrue\tfalse", "abc", "false", "yes", "true");

        expect("numbers print without a trailing .0",
                "print(3.0)\nprint(1e3)\nprint(0x10)\nprint(1.5)",
                "3", "1000", "16", "1.5");
    }

    private static void moreLanguage() {
        expect("string methods through colon syntax",
                "local s = 'Radar'\nprint(s:upper())\nprint(('x'):rep(3))",
                "RADAR", "xxx");

        expect("records as tables",
                "local contact = {name = 'drone', range = 120, threat = 40}\n"
                        + "local function describe(c)\n"
                        + "  return string.format('%s at %dm (%d)', c.name, c.range, c.threat)\nend\n"
                        + "print(describe(contact))",
                "drone at 120m (40)");

        expect("nested tables and mutation",
                "local rack = {slots = {}}\nrack.slots[1] = {tier = 'basic', cores = 1}\n"
                        + "rack.slots[2] = {tier = 'advanced', cores = 2}\n"
                        + "local total = 0\nfor i = 1, #rack.slots do total = total + rack.slots[i].cores end\n"
                        + "print(total)",
                "3");

        expect("while with a numeric accumulator",
                "local n, steps = 27, 0\nwhile n ~= 1 do\n"
                        + "  if n % 2 == 0 then n = n / 2 else n = 3 * n + 1 end\n  steps = steps + 1\n"
                        + "end\nprint(steps)",
                "111");

        expect("table.sort with a comparator",
                "local t = {{n='b',p=2},{n='a',p=3},{n='c',p=1}}\n"
                        + "table.sort(t, function(x, y) return x.p > y.p end)\n"
                        + "print(t[1].n, t[2].n, t[3].n)",
                "a\tb\tc");
    }

    private static void control() {
        expect("if / elseif / else",
                "local function grade(n)\n"
                        + "  if n > 90 then return 'A' elseif n > 80 then return 'B' else return 'C' end\n"
                        + "end\nprint(grade(95), grade(85), grade(10))",
                "A\tB\tC");

        expect("while with break",
                "local i = 0\nwhile true do i = i + 1 if i == 4 then break end end\nprint(i)",
                "4");

        expect("repeat/until sees body locals",
                "local n = 0\nrepeat local step = 2 n = n + step until n >= 6\nprint(n)",
                "6");

        expect("numeric for, ascending and descending",
                "local sum = 0\nfor i = 1, 5 do sum = sum + i end\nprint(sum)\n"
                        + "for i = 3, 1, -1 do io = i end\nprint(io)",
                "15", "1");

        expect("nested loops and labelled scopes",
                "local count = 0\nfor i = 1, 3 do for j = 1, 3 do count = count + 1 end end\nprint(count)",
                "9");
    }

    private static void functions() {
        expect("recursion",
                "local function fact(n) if n <= 1 then return 1 end return n * fact(n - 1) end\n"
                        + "print(fact(6))",
                "720");

        expect("multiple returns",
                "local function pair() return 1, 2 end\nlocal a, b = pair()\nprint(a, b)\n"
                        + "print(pair())",
                "1\t2", "1\t2");

        expect("varargs",
                "local function sum(...)\n  local total = 0\n"
                        + "  for _, v in ipairs({...}) do total = total + v end\n  return total\nend\n"
                        + "print(sum(1, 2, 3, 4))",
                "10");

        expect("closures keep their own counter",
                "local function counter()\n  local n = 0\n"
                        + "  return function() n = n + 1 return n end\nend\n"
                        + "local a = counter()\nlocal b = counter()\n"
                        + "print(a(), a(), a(), b())",
                "1\t2\t3\t1");

        expect("methods and self",
                "local account = {balance = 0}\n"
                        + "function account:deposit(v) self.balance = self.balance + v end\n"
                        + "account:deposit(10)\naccount:deposit(5)\nprint(account.balance)",
                "15");

        expect("defaults via or",
                "local function greet(name) name = name or 'operator' return 'hi ' .. name end\n"
                        + "print(greet())\nprint(greet('you'))",
                "hi operator", "hi you");

        expect("nested closures over two levels",
                "local function outer(a)\n  return function(b)\n    return function(c) return a + b + c end\n"
                        + "  end\nend\nprint(outer(1)(2)(3))",
                "6");
    }

    private static void tables() {
        expect("table constructor and fields",
                "local t = {1, 2, 3, name = 'pad', [10] = 'ten'}\n"
                        + "print(#t, t[1], t.name, t[10])",
                "3\t1\tpad\tten");

        expect("table library",
                "local t = {3, 1, 2}\ntable.sort(t)\nprint(table.concat(t, ','))\n"
                        + "table.insert(t, 4)\nprint(#t, t[4])\n"
                        + "print(table.remove(t, 1), #t)",
                "1,2,3", "4\t4", "1\t3");

        expect("pairs visits every key",
                "local t = {a = 1, b = 2, c = 3}\nlocal count = 0\n"
                        + "for k, v in pairs(t) do count = count + v end\nprint(count)",
                "6");

        expect("ipairs stops at the first hole",
                "local t = {1, 2, nil, 4}\nlocal n = 0\nfor i, v in ipairs(t) do n = n + 1 end\nprint(n)",
                "2");

        expect("assignment swaps values",
                "local a, b = 1, 2\na, b = b, a\nprint(a, b)",
                "2\t1");

        expect("multiple assignment into a table",
                "local t = {}\nt[1], t[2] = 'x', 'y'\nprint(t[1], t[2])",
                "x\ty");
    }

    private static void strings() {
        expect("string functions",
                "print(string.len('hello'))\nprint(string.sub('computer', 1, 4))\n"
                        + "print(string.upper('ab'), string.lower('AB'))\nprint(string.rep('ab', 3))",
                "5", "comp", "AB\tab", "ababab");

        // "%%" is a literal percent and must not consume an argument. Getting this wrong shifted every
        // later argument by one - the barrage example program caught it.
        expect("string.format percent",
                "print(string.format('%d%% done, %d left', 25, 3))\n"
                        + "print(string.format('100%%'))",
                "25% done, 3 left", "100%");
        expect("string.format",
                "print(string.format('%s has %d cores at %d MHz', 'cpu', 4, 3200))\n"
                        + "print(string.format('%.2f', 3.14159))",
                "cpu has 4 cores at 3200 MHz", "3.14");

        expect("plain find and gsub",
                "print(string.find('radar scan', 'scan'))\nprint(string.gsub('a-b-c', '-', '_'))",
                "7\t10", "a_b_c\t2");
    }

    private static void errors() {
        expect("pcall catches",
                "local ok, message = pcall(function() error('boom') end)\nprint(ok)\nprint(message)",
                "false", "boom");

        expect("pcall passes results through",
                "local ok, value = pcall(function() return 42 end)\nprint(ok, value)",
                "true\t42");

        expect("assertion failure is catchable",
                "local ok = pcall(function() assert(false, 'nope') end)\nprint(ok)",
                "false");
    }

    private static void coroutines() {
        expect("coroutine yields and resumes",
                "local co = coroutine.create(function(a)\n"
                        + "  local b = coroutine.yield(a + 1)\n"
                        + "  return b * 2\nend)\n"
                        + "print(coroutine.resume(co, 1))\nprint(coroutine.resume(co, 10))",
                "true\t2", "true\t20");

        expect("coroutine completes and reports dead",
                "local co = coroutine.create(function() end)\n"
                        + "print(coroutine.resume(co))\nprint(coroutine.status(co))",
                "true", "dead");

        expect("wrapped coroutine as an iterator",
                "local function gen(n)\n  return coroutine.wrap(function()\n"
                        + "    for i = 1, n do coroutine.yield(i) end\n  end)\nend\n"
                        + "local total = 0\nfor v in gen(4) do total = total + v end\nprint(total)",
                "10");
    }

    private static void budgets() {
        // A runaway script must pause and hand the server its tick back, not hang.
        Vm vm = Lua.newVm();
        List<String> lines = new ArrayList<>();
        vm.out = lines::add;
        LuaFunction loop = Lua.load("while true do end", "=loop");
        vm.pushEntry(loop, new Object[0]);
        Vm.Status status = vm.run(5_000);
        check("runaway loop pauses instead of hanging", status == Vm.Status.PAUSED);
        check("paused VM can be stepped again", vm.run(5_000) == Vm.Status.PAUSED);
        vm.reset();

        // A script that waits is suspended, and resumes with the value it was given.
        Vm waiting = Lua.newVm();
        List<String> out = new ArrayList<>();
        waiting.out = out::add;
        waiting.globals.set("pull", new LuaFunction.NativeFunction("pull", (machine, args) -> {
            throw new Vm.Yield("event", 100L);
        }));
        LuaFunction script = Lua.load("local v = pull()\nprint('got ' .. v)", "=wait");
        waiting.pushEntry(script, new Object[0]);
        boolean yielded = false;
        try {
            waiting.run(10_000);
        } catch (Vm.Yield expected) {
            yielded = true;
        }
        check("event.wait suspends the script", yielded);
        check("suspended script is still paused", waiting.paused());
        waiting.resumeWith("signal");
        waiting.run(10_000);
        eq("resumed with the waited-for value", "got signal", out.isEmpty() ? "<nothing>" : out.get(0));
    }

    // ------------------------------------------------------------------ plumbing

    private static void expect(String label, String source, String... expectedLines) {
        Vm vm = Lua.newVm();
        List<String> lines = new ArrayList<>();
        vm.out = lines::add;
        try {
            Lua.run(source, vm);
        } catch (LuaError error) {
            failures++;
            checks++;
            System.out.println("FAIL " + label + ": raised " + error.getMessage());
            return;
        }
        if (lines.size() != expectedLines.length) {
            checks++;
            failures++;
            System.out.println("FAIL " + label + ": expected " + expectedLines.length
                    + " line(s), got " + lines.size() + " -> " + lines);
            return;
        }
        boolean ok = true;
        for (int i = 0; i < expectedLines.length; i++) {
            if (!expectedLines[i].equals(lines.get(i))) {
                ok = false;
                checks++;
                failures++;
                System.out.println("FAIL " + label + " line " + (i + 1) + ": expected '"
                        + expectedLines[i] + "' but was '" + lines.get(i) + "'");
            }
        }
        if (ok) {
            checks++;
            System.out.println("ok   " + label);
        }
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

    private static void eq(String label, String expected, String actual) {
        checks++;
        if (expected.equals(actual)) {
            System.out.println("ok   " + label);
        } else {
            failures++;
            System.out.println("FAIL " + label + ": expected '" + expected + "' but was '" + actual + "'");
        }
    }
}
