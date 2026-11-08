"""Compile and run the Robolectric watch-flow suite with a prepared dependency cache."""
import argparse
import os
import pathlib
import subprocess
import sys
import zipfile


def prop(path):
    return str(path).replace("\\", "/").encode("unicode_escape").decode("ascii").replace("\\x", "\\u00")


parser = argparse.ArgumentParser()
for name in ("test-root", "build", "sdk", "jdk", "work", "shots"):
    parser.add_argument("--" + name, required=True, type=pathlib.Path)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
test_root, build, work = args.test_root.resolve(), args.build.resolve(), args.work.resolve()
classes, extracted = work / "classes", work / "aar"
classes.mkdir(parents=True, exist_ok=True)
extracted.mkdir(parents=True, exist_ok=True)
args.shots.resolve().mkdir(parents=True, exist_ok=True)

dependencies = []
for item in (test_root / "classpath.txt").read_text(encoding="utf-8").strip().split(os.pathsep):
    path = pathlib.Path(item)
    if path.suffix == ".aar":
        target = extracted / path.stem
        target.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path) as archive:
            archive.extract("classes.jar", target)
        dependencies.append(str(target / "classes.jar"))
    else:
        dependencies.append(str(path))

config = classes / "com/android/tools"
config.mkdir(parents=True, exist_ok=True)
source = root / "app/src/main"
(config / "test_config.properties").write_text(
    "android_merged_manifest=" + prop(build / "AndroidManifest.xml") + "\n"
    "android_merged_resources=" + prop(source / "res") + "\n"
    "android_merged_assets=" + prop(source / "assets") + "\n"
    "android_custom_package=cn.wordhalo.watch\n"
    "android_resource_apk=" + prop(build / "base.apk") + "\n",
    encoding="ascii",
)

classpath = os.pathsep.join([str(classes), str(build / "classes"), *dependencies, str(args.sdk.resolve())])
javac, java = args.jdk.resolve() / "bin/javac.exe", args.jdk.resolve() / "bin/java.exe"
subprocess.run([str(javac), "-encoding", "UTF-8", "-cp", classpath, "-d", str(classes), str(root / "tests/DeviceFlowTest.java")], check=True)
runtime = test_root / "runtime"
command = [
    str(java), "-Xmx1800m", "-Dfile.encoding=UTF-8", "-Duser.language=en",
    "-Drobolectric.dependency.repo.url=https://repo.maven.apache.org/maven2",
    "-Drobolectric.dependency.dir=" + str(runtime),
    "-Drobolectric.dependency.repo.id=central", "-Drobolectric.graphicsMode=NATIVE",
    "-Duser.home=" + str(work / "home"), "-Dshots=" + str(args.shots.resolve()),
    "-cp", classpath, "org.junit.runner.JUnitCore", "cn.wordhalo.watch.DeviceFlowTest",
]
sys.exit(subprocess.run(command).returncode)
