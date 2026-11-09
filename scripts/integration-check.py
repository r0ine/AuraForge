import argparse
import os
from pathlib import Path
import queue
import shutil
import socket
import subprocess
import threading
import time

parser = argparse.ArgumentParser(description="AuraForge için ayrı Paper sunucusunda oyun içi doğrulama")
parser.add_argument("--mineflayer", type=Path, required=True)
parser.add_argument("--paper", type=Path, required=True, help="Paper 1.21.4 sunucu jarı")
parser.add_argument("--port", type=int, default=25586)
parser.add_argument("--java", default="java", help="Java 21 çalıştırıcısı")
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
server = root / "build" / f"integration-server-{int(time.time())}"
with socket.socket() as probe:
    probe.bind(("127.0.0.1", args.port))
server.mkdir(parents=True)
(server / "plugins").mkdir()
shutil.copy2(args.paper.resolve(), server / "paper.jar")
(server / "eula.txt").write_text("eula=true\n", encoding="utf-8")
shutil.copy2(root / "build/libs/AuraForge-1.2.0.jar", server / "plugins/AuraForge.jar")
shutil.copy2(root / "build/libs/AuraForgeChecks.jar", server / "plugins/AuraForgeChecks.jar")
(server / "server.properties").write_text(
    f"server-ip=127.0.0.1\nserver-port={args.port}\nonline-mode=false\nenforce-secure-profile=false\n"
    "view-distance=3\nsimulation-distance=3\nspawn-protection=0\nlevel-type=minecraft:flat\n"
    "generate-structures=false\nspawn-monsters=false\nmax-players=2\nmax-tick-time=60000\n",
    encoding="utf-8",
)
environment = os.environ | {"MINEFLAYER_MODULE": str(args.mineflayer.resolve()), "FORGE_TEST_PORT": str(args.port)}
creation_flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
print(f"Kontrol sunucusu: {server}", flush=True)

for phase, marker in enumerate(("ADVENTURE_CHECKS_PASSED", "ADVENTURE_PERSISTENCE_PASSED"), start=1):
    lines = queue.Queue()
    with (server / f"phase-{phase}.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(
            [args.java, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Xms256M", "-Xmx768M", "-jar", "paper.jar", "--nogui"],
            cwd=server, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding="utf-8", errors="replace", creationflags=creation_flags,
        )

        def collect():
            for line in process.stdout:
                log.write(line)
                log.flush()
                lines.put(line)

        reader = threading.Thread(target=collect, daemon=True)
        reader.start()
        bot = None
        passed = False
        try:
            deadline = time.monotonic() + 150
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError(f"Sunucu beklenmedik biçimde kapandı: {process.returncode}")
                try:
                    line = lines.get(timeout=1)
                except queue.Empty:
                    continue
                if "Done (" in line and bot is None:
                    bot = subprocess.Popen(["node", str(root / "scripts/integration-bot.cjs")], env=environment, creationflags=creation_flags)
                if "AuraForge" in line or "Done (" in line or "AssertionError" in line:
                    print(line.strip(), flush=True)
                if "ADVENTURE_CHECKS_FAILED" in line:
                    raise RuntimeError(f"Oyun içi doğrulama başarısız: {server / f'phase-{phase}.log'}")
                if marker in line:
                    passed = True
                    break
            if not passed:
                raise TimeoutError("Doğrulama süresi doldu.")
        finally:
            if bot is not None and bot.poll() is None:
                bot.terminate()
                bot.wait(timeout=10)
            if process.poll() is None:
                process.stdin.write("stop\n")
                process.stdin.flush()
                try:
                    process.wait(timeout=40)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
            reader.join(timeout=5)
print("İki aşama geçti; etkin kullanıcı sunucusuna dokunulmadı.", flush=True)
