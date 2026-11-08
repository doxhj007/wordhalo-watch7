"""Build with official Android SDK tools; no Gradle download is required.
Example: python tools/build.py --platform SDK/platforms/android-36 --tools SDK/build-tools/36.0.0 --jdk JDK --work BUILD --key PRIVATE_KEY_DIR --out app.apk
The private signing key must be retained to install updates without losing progress.
"""
import argparse,pathlib,subprocess,zipfile,secrets,os,shutil,hashlib,json,sys
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
p=argparse.ArgumentParser()
for k in ['platform','tools','jdk','work','key','out']:p.add_argument('--'+k,required=True)
a=p.parse_args();root=pathlib.Path(__file__).resolve().parents[1];src=root/'app/src/main'
platform=pathlib.Path(a.platform).resolve();tools=pathlib.Path(a.tools).resolve();jdk=pathlib.Path(a.jdk).resolve();work=pathlib.Path(a.work).resolve();key=pathlib.Path(a.key).resolve();out=pathlib.Path(a.out).resolve()
for d in [work,key,out.parent,work/'gen',work/'classes',work/'dex']:d.mkdir(parents=True,exist_ok=True)
ext='.exe' if os.name=='nt' else ''
def run(*args):
 # aapt2 on Windows mishandles non-ASCII absolute argument paths; relative paths
 # work correctly even when the checkout itself has a Chinese directory name.
 result=subprocess.run([os.path.relpath(x) if isinstance(x,pathlib.Path) else str(x) for x in args],capture_output=True,text=True,encoding='utf-8',errors='replace')
 if result.stdout: print(result.stdout)
 if result.stderr: print(result.stderr)
 if result.returncode:raise RuntimeError('Build command failed: '+str(args[0]))
java=jdk/'bin'/('java'+ext);android=platform/'android.jar'
run(tools/('aapt2'+ext),'compile','--dir',src/'res','-o',work/'resources.zip')
(work/'AndroidManifest.xml').write_text((src/'AndroidManifest.xml').read_text(encoding='utf-8').replace('<manifest ','<manifest package="cn.wordhalo.watch" ',1),encoding='utf-8')
run(tools/('aapt2'+ext),'link','-o',work/'base.apk','-I',android,'--manifest',work/'AndroidManifest.xml','--java',work/'gen','-A',src/'assets','--min-sdk-version','30','--target-sdk-version','36',work/'resources.zip')
sources=[*src.joinpath('java').rglob('*.java'),*work.joinpath('gen').rglob('*.java')]
run(jdk/'bin'/('javac'+ext),'-J-Duser.language=en','--release','11','-encoding','UTF-8','-classpath',android,'-d',work/'classes',*sources)
run(jdk/'bin'/('jar'+ext),'--create','--file',work/'classes.jar','-C',work/'classes','.')
run(java,'-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--min-api','30','--output',work/'dex',work/'classes.jar')
shutil.copy2(work/'base.apk',work/'unsigned.apk')
with zipfile.ZipFile(work/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
 for dex in (work/'dex').glob('*.dex'):z.write(dex,dex.name)
run(tools/('zipalign'+ext),'-f','-P','16','4',work/'unsigned.apk',work/'aligned.apk')
ks=key/'wordhalo.p12';pw=key/'password.txt'
if not ks.exists():
 pw.write_text(secrets.token_urlsafe(32),encoding='ascii')
 run(jdk/'bin'/('keytool'+ext),'-genkeypair','-keystore',ks,'-storetype','PKCS12','-storepass:file',pw,'-alias','wordhalo','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=WordHalo Personal, O=Personal, C=CN')
run(java,'-jar',tools/'lib/apksigner.jar','sign','--ks',ks,'--ks-pass','file:'+os.path.relpath(pw),'--ks-key-alias','wordhalo','--out',out,work/'aligned.apk')
run(java,'-jar',tools/'lib/apksigner.jar','verify','--verbose','--print-certs',out)
run(tools/('zipalign'+ext),'-c','-P','16','4',out)
report={'apk':out.name,'bytes':out.stat().st_size,'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'compileSdk':36,'targetSdk':36,'minSdk':30}
(work/'build-report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
