import React from 'react';
import {Text, View, useWindowDimensions} from 'react-native';
import {useTheme} from '../theme/ThemeContext';

type TokenType = 'keyword' | 'builtin' | 'string' | 'comment' | 'number' | 'operator' | 'blank' | 'text';
type Token = {type: TokenType; value: string};

// 코드 블록은 라이트/다크 상관없이 항상 어두운 에디터 배경을 쓴다.
// 아래 색은 앱 테마가 아니라 문법 강조 테마(VS Code Dark+)이므로 토큰화하지 않음.
const TOKEN_COLORS: Record<TokenType, string> = {
  keyword:  '#569CD6',
  builtin:  '#DCDCAA',
  string:   '#CE9178',
  comment:  '#6A9955',
  number:   '#B5CEA8',
  operator: '#D4D4D4',
  blank:    '#FF9966',
  text:     '#D4D4D4',
};

const PY_KEYWORDS = new Set([
  'def','return','if','elif','else','for','while','in','not','and','or',
  'True','False','None','import','from','class','with','as','try','except',
  'pass','break','continue','lambda','yield','del','global','nonlocal',
  'raise','assert','is',
]);

const PY_BUILTINS = new Set([
  'len','range','print','int','str','float','bool','list','dict','set',
  'tuple','isinstance','enumerate','zip','map','filter','sorted','reversed',
  'sum','min','max','abs','round','input','append','popleft','deque',
]);

const JS_KEYWORDS = new Set([
  'function','return','if','else','for','while','in','of','new','const',
  'let','var','true','false','null','undefined','class','extends','import',
  'export','default','this','typeof','instanceof','break','continue','do',
  'switch','case','try','catch','finally','throw','delete','void','async','await',
]);

const JS_BUILTINS = new Set([
  'console','Math','Array','Object','String','Number','Boolean','Set','Map',
  'Promise','JSON','parseInt','parseFloat','isNaN','log','push','pop','shift',
  'unshift','splice','slice','indexOf','includes','length','keys','values',
  'has','get','floor','ceil','round','max','min','abs','size',
]);

function tokenizeLine(line: string, lang: 'python' | 'javascript'): Token[] {
  const keywords = lang === 'python' ? PY_KEYWORDS : JS_KEYWORDS;
  const builtins = lang === 'python' ? PY_BUILTINS : JS_BUILTINS;
  const tokens: Token[] = [];
  let i = 0;

  while (i < line.length) {
    if (line.slice(i, i + 3) === '___') {
      tokens.push({type: 'blank', value: '___'});
      i += 3;
      continue;
    }
    if (line[i] === '#') {
      tokens.push({type: 'comment', value: line.slice(i)});
      break;
    }
    if (lang === 'javascript' && line.slice(i, i + 2) === '//') {
      tokens.push({type: 'comment', value: line.slice(i)});
      break;
    }
    if (line[i] === '"' || line[i] === "'") {
      const q = line[i];
      if (line.slice(i, i + 3) === q + q + q) {
        const end = line.indexOf(q + q + q, i + 3);
        const endIdx = end === -1 ? line.length : end + 3;
        tokens.push({type: 'string', value: line.slice(i, endIdx)});
        i = endIdx;
      } else {
        let j = i + 1;
        while (j < line.length && line[j] !== q) {
          if (line[j] === '\\') {j++;}
          j++;
        }
        tokens.push({type: 'string', value: line.slice(i, Math.min(j + 1, line.length))});
        i = Math.min(j + 1, line.length);
      }
      continue;
    }
    if (lang === 'javascript' && line[i] === '`') {
      let j = i + 1;
      while (j < line.length && line[j] !== '`') {j++;}
      tokens.push({type: 'string', value: line.slice(i, j + 1)});
      i = j + 1;
      continue;
    }
    if (/[0-9]/.test(line[i])) {
      let j = i;
      while (j < line.length && /[0-9.]/.test(line[j])) {j++;}
      tokens.push({type: 'number', value: line.slice(i, j)});
      i = j;
      continue;
    }
    if (/[a-zA-Z_]/.test(line[i])) {
      let j = i;
      while (j < line.length && /[a-zA-Z0-9_]/.test(line[j])) {j++;}
      const word = line.slice(i, j);
      if (keywords.has(word)) {
        tokens.push({type: 'keyword', value: word});
      } else if (builtins.has(word)) {
        tokens.push({type: 'builtin', value: word});
      } else {
        tokens.push({type: 'text', value: word});
      }
      i = j;
      continue;
    }
    if (/[+\-*/%=<>!&|^~@]/.test(line[i])) {
      let j = i;
      while (j < line.length && /[+\-*/%=<>!&|^~@]/.test(line[j])) {j++;}
      tokens.push({type: 'operator', value: line.slice(i, j)});
      i = j;
      continue;
    }
    tokens.push({type: 'text', value: line[i]});
    i++;
  }

  return tokens;
}

// 한글(CJK)은 영문의 2배 너비로 계산
function measureWidth(s: string): number {
  let w = 0;
  for (const ch of s) {
    const code = ch.charCodeAt(0);
    w += (code >= 0xAC00 && code <= 0xD7FF) || (code >= 0x4E00 && code <= 0x9FFF) ? 2 : 1;
  }
  return w;
}

// 한 줄에서 인라인 주석(# 또는 //) 시작 위치를 반환, 없으면 -1
function findInlineComment(line: string, lang: 'python' | 'javascript'): number {
  let inStr = false;
  let strCh = '';
  for (let i = 0; i < line.length; i++) {
    if (inStr) {
      if (line[i] === strCh) {inStr = false;}
    } else if (line[i] === '"' || line[i] === "'") {
      inStr = true;
      strCh = line[i];
    } else if (lang === 'python' && line[i] === '#') {
      return i;
    } else if (lang === 'javascript' && line.slice(i, i + 2) === '//') {
      return i;
    }
  }
  return -1;
}

// 코드 줄바꿈(nesting 정렬): 긴 줄을 '최상위 여는 괄호' 바로 뒤에 맞춰 정렬(hanging indent)하고,
// 괄호 안 최상위(depth 1) 콤마 뒤 / 최상위(depth 0) 논리연산자(and·or·&&·||) 앞에서 끊는다.
function wrapCodePart(line: string, maxChars: number): string[] {
  if (measureWidth(line) <= maxChars) {return [line];}

  const leading = line.match(/^\s*/)?.[0] ?? '';

  // 정렬 칸 = 최상위 여는 괄호 바로 뒤. 없거나 너무 깊으면 들여쓰기+4로 폴백
  let alignCol = leading.length + 4;
  let depth = 0;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (ch === '(' || ch === '[' || ch === '{') {
      if (depth === 0) {alignCol = i + 1;}
      depth++;
    } else if (ch === ')' || ch === ']' || ch === '}') {
      depth--;
    }
  }
  if (alignCol > maxChars - 12) {alignCol = leading.length + 4;}
  const cont = ' '.repeat(alignCol);

  // 끊을 위치 수집
  const breaks: number[] = [];
  depth = 0;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (ch === '(' || ch === '[' || ch === '{') {depth++;}
    else if (ch === ')' || ch === ']' || ch === '}') {depth--;}
    else if (ch === ',' && depth === 1) {breaks.push(i + 1);}                       // 콤마 뒤
    else if (depth === 0 && ch === ' ' && /^\s+(and|or|&&|\|\|)\s/.test(line.slice(i))) {
      breaks.push(i);                                                              // 논리연산자 앞
    }
  }

  // 세그먼트 분할
  const segs: string[] = [];
  let prev = 0;
  for (const b of breaks) {
    if (b > prev) {segs.push(line.slice(prev, b)); prev = b;}
  }
  segs.push(line.slice(prev));

  // maxChars에 맞춰 욕심껏 채우고, 넘으면 정렬 칸에서 새 줄
  const result: string[] = [];
  let buf = '';
  for (let k = 0; k < segs.length; k++) {
    const raw = segs[k];
    if (buf === '') {
      buf = k === 0 ? raw : cont + raw.replace(/^\s+/, '');
    } else if (measureWidth(buf + raw) <= maxChars) {
      buf += raw;
    } else {
      result.push(buf.replace(/\s+$/, ''));
      buf = cont + raw.replace(/^\s+/, '');
    }
  }
  if (buf !== '') {result.push(buf.replace(/\s+$/, ''));}

  return result;
}

// 줄바꿈 진입점
// - 순수 주석 줄(# 또는 //로 시작): 절대 자르지 않음
// - 인라인 주석 포함 줄: 코드 부분만 자르고, 주석은 별도 줄로 분리
function wrapLine(line: string, maxChars: number, lang: 'python' | 'javascript'): string[] {
  if (/^\s*(#|\/\/)/.test(line)) {return [line];}

  const commentIdx = findInlineComment(line, lang);
  if (commentIdx !== -1) {
    const codePart = line.slice(0, commentIdx).trimEnd();
    const commentPart = line.slice(commentIdx);
    const indent = line.match(/^(\s*)/)?.[1] ?? '';
    const codeLines = wrapCodePart(codePart, maxChars);
    return [...codeLines, indent + commentPart];
  }

  return wrapCodePart(line, maxChars);
}

// 괄호 균형(문자열/주석 무시). >0이면 식이 아직 안 닫힘.
function bracketDepth(s: string): number {
  let d = 0;
  let inStr = false;
  let q = '';
  for (let i = 0; i < s.length; i++) {
    const ch = s[i];
    if (inStr) {
      if (ch === q && s[i - 1] !== '\\') {inStr = false;}
      continue;
    }
    if (ch === '"' || ch === "'") {inStr = true; q = ch; continue;}
    if (ch === '#') {break;}
    if (ch === '(' || ch === '[' || ch === '{') {d++;}
    else if (ch === ')' || ch === ']' || ch === '}') {d--;}
  }
  return d;
}

// 식 한복판에서 끊긴 물리적 줄을 논리적 줄로 합친다(저장된 코드에 박힌 줄바꿈 정리).
function reflow(code: string): string[] {
  const out: string[] = [];
  let buf = '';
  for (const line of code.split('\n')) {
    buf = buf === '' ? line : buf.replace(/\s+$/, '') + line.replace(/^\s+/, '');
    if (bracketDepth(buf) <= 0) {
      out.push(buf);
      buf = '';
    }
  }
  if (buf !== '') {out.push(buf);}
  return out;
}

function detectLanguage(code: string): 'python' | 'javascript' {
  if (/\bdef\b|\bfrom\s+\w+\s+import\b/.test(code)) {return 'python';}
  return 'javascript';
}

// Python 블록 들여쓰기 재정렬: ':'로 끝나면 다음 줄부터 +1단(4칸),
// else/elif/except/finally는 한 단 내려서 정렬, return/pass/break/continue/raise 뒤엔 한 단 빠짐.
function reindentPython(code: string): string {
  const dedentStart = /^(else|elif|except|finally|case|default)\b/;
  const dedentAfter = /^(return|pass|break|continue|raise)\b/;
  const out: string[] = [];
  let indent = 0;
  for (const raw of code.split('\n')) {
    const line = raw.trim();
    if (line === '') {out.push(''); continue;}
    let ind = indent;
    if (dedentStart.test(line)) {ind = Math.max(0, indent - 1);}
    out.push('    '.repeat(ind) + line);
    const noComment = line.replace(/#.*$/, '').trimEnd();
    if (noComment.endsWith(':')) {
      indent = (dedentStart.test(line) ? ind : indent) + 1;
    } else if (dedentAfter.test(line)) {
      indent = Math.max(0, indent - 1);
    }
  }
  return out.join('\n');
}

// 편집란(TextInput) 등에서 코드를 '정렬': Python이면 블록 들여쓰기 재정렬 후
// 식 줄 합치고(reflow) 긴 줄을 정렬 줄바꿈한다.
// language를 명시하면 자동감지(detectLanguage) 대신 그걸 쓴다 — 구현 문제의 본문 조각처럼
// 'def'가 없어 오판되는 경우를 막기 위함. ('Python'/'Java'/'C++' 모두 허용)
export function formatCode(code: string, maxChars = 42, language?: string): string {
  const isPython = language ? /python/i.test(language) : detectLanguage(code) === 'python';
  const lang: 'python' | 'javascript' = isPython ? 'python' : 'javascript';
  const src = isPython ? reindentPython(code) : code;
  return reflow(src)
    .flatMap(line => wrapLine(line, maxChars, lang))
    .join('\n');
}

export default function CodeBlock({
  code,
  language,
}: {
  code: string;
  language?: 'python' | 'javascript';
}) {
  const {fontScale} = useTheme();
  const {width} = useWindowDimensions();
  const lang = language ?? detectLanguage(code);
  const fontSize = 13 * fontScale;
  const lineHeight = fontSize * 1.75;

  // 모노스페이스 한 글자 너비 ≈ fontSize * 0.605
  // padding 32 (양쪽 16씩) 제외
  const maxChars = Math.floor((width - 32) / (fontSize * 0.605));

  const visualLines = reflow(code).flatMap(line => wrapLine(line, maxChars, lang));

  return (
    <View style={{backgroundColor: '#1E1E2E', borderRadius: 10, padding: 16, marginBottom: 20}}>
      {visualLines.map((line, lineIdx) => (
        <View key={lineIdx} style={{flexDirection: 'row', flexWrap: 'wrap', minHeight: lineHeight}}>
          {tokenizeLine(line, lang).map((token, tokenIdx) => (
            <Text
              key={tokenIdx}
              style={{
                fontFamily: 'monospace',
                fontSize,
                lineHeight,
                color: TOKEN_COLORS[token.type],
                textDecorationLine: token.type === 'blank' ? 'underline' : 'none',
              }}>
              {token.value}
            </Text>
          ))}
        </View>
      ))}
    </View>
  );
}
