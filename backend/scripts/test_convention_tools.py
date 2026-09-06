#!/usr/bin/env python3
"""Spotless·Checkstyle 설정 변경 후 실행하는 오류 주입 검사. AI·DB 호출 없음.

backend에서 python3 scripts/test_convention_tools.py 로 실행한다.
운영 코드는 수정하지 않고 build/convention-probe의 가상 Java 파일만 수정한다.
Gradle 의존성을 내려받을 네트워크와 JDK가 필요하다.
"""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
probe = root / 'build/convention-probe'
source = probe / 'checkstyle/Probe.java'
source.parent.mkdir(parents=True, exist_ok=True)
initialization = probe / 'probe.init.gradle'
initialization.write_text("""
gradle.projectsEvaluated {
    rootProject.spotless.java {
        target('build/convention-probe/FormatProbe.java')
        ratchetFrom(null)
    }
    rootProject.tasks.named('checkstyleMain') {
        setSource(rootProject.fileTree('build/convention-probe/checkstyle'))
        reports.xml.outputLocation = rootProject.layout.buildDirectory.file('convention-probe/checkstyle.xml')
        reports.html.required = false
    }
}
""", encoding='utf-8')
command = [str(root / 'gradlew'), '--console=plain', '-I', str(initialization)]

def run(task, success):
    result = subprocess.run(command + task.split(), cwd=root, capture_output=True, text=True)
    (probe / (task.replace(' ', '-') + '.log')).write_text(result.stdout + result.stderr)
    if (result.returncode == 0) != success:
        raise AssertionError(result.stdout + result.stderr)

source.write_text('''class Probe {
    int BadField;
    void run(int BadParameter) {
        int BadLocal = 0;
        if (BadParameter > 0) return;
        else { BadLocal++; }
        switch (BadLocal) { default: break; }
        while (BadParameter > 0) {}
    }
}
record ProbeRequest(String BadComponent) {}
''')
run('checkstyleMain', False)
errors = list(ET.parse(probe / 'checkstyle.xml').iter('error'))
for check in ['NeedBraces', 'IllegalToken', 'EmptyBlock', 'MemberName', 'ParameterName', 'LocalVariableName', 'RecordComponentName']:
    assert any(check in item.attrib['source'] for item in errors), check
tokens = [item for item in errors if 'IllegalToken' in item.attrib['source']]
assert {item.attrib['line'] for item in tokens} == {'6', '7'}
print('PASS: Checkstyle catches all seven configured violation types, including both else and switch')
source.write_text('''class Probe {
    private int value;
    void run(int value) {
        if (value > 0) { return; }
        this.value = value;
    }
}
record ProbeRequest(String name) {}
''')
legacy = source.parent / 'admin/infrastructure/persistence/AdminTopicQueryRepositoryImpl.java'
legacy.parent.mkdir(parents=True, exist_ok=True)
legacy.write_text('''class AdminTopicQueryRepositoryImpl {
    void appendOrder(int sort) { switch (sort) { default: break; } }
    void other(int value) { switch (value) { default: break; } }
}
''')
run('checkstyleMain', False)
errors = list(ET.parse(probe / 'checkstyle.xml').iter('error'))
assert len(errors) == 1 and errors[0].attrib['line'] == '3', [x.attrib for x in errors]
print('PASS: Legacy exclusion does not hide a switch in another method')
legacy.unlink()
run('checkstyleMain', True)
print('PASS: Valid source passes Checkstyle')
formatted = probe / 'FormatProbe.java'
formatted.write_text('import java.util.List;\r\nclass FormatProbe{\r\n\tvoid run(){System.out.println("ok");}   \r\n}')
run('spotlessCheck', False)
run('spotlessApply', True)
text = formatted.read_text()
assert 'import java.util.List' not in text
assert '\t' not in text and '\r' not in formatted.read_bytes().decode()
assert '    void run() {' in text and text.endswith('\n')
assert all(line == line.rstrip() for line in text.splitlines())
formatted.write_text(text + '\nclass Parameters {\n    void run(\n            Long first,\n            Long second,\n            Long third\n    ) {\n        System.out.println(first);\n    }\n}\n')
run('spotlessApply', True)
text = formatted.read_text()
assert 'Long third\n    ) {' in text, text
assert '            Long first,' in text, text
run('spotlessCheck', True)
run('spotlessApply', True)
assert formatted.read_text() == text
print('PASS: Spotless detects, fixes and preserves the corrected format')
