/**
 * GitHub식 5×5 대칭 아이덴티콘 — seed(주제 id) 해시로 블록 on/off + 색을 결정한다.
 * 좌측 3열을 우측에 미러링한 각진 기하 패턴. 색은 Claude 웜 팔레트(앰버~코랄 대역) 안에서 분포.
 */
interface Props {
  seed: string;
  size?: number;
}

export function Identicon({ seed, size = 28 }: Props) {
  const h = hashSeed(seed);
  // 색조는 전체 대역에서 자유롭게(주제 구분용). 채도/명도만 다크 UI에 맞게 절제.
  const hue = (h >>> 9) % 360;
  const fill = `hsl(${hue} 58% 60%)`;

  const blocks: { x: number; y: number }[] = [];
  for (let col = 0; col < 3; col++) {
    for (let row = 0; row < 5; row++) {
      if (((h >> (col * 5 + row)) & 1) === 0) continue;
      const xs = col === 2 ? [2] : [col, 4 - col];
      for (const x of xs) blocks.push({ x, y: row });
    }
  }

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 5 5"
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      {blocks.map((b) => (
        <rect key={`${b.x}-${b.y}`} x={b.x} y={b.y} width={1} height={1} fill={fill} />
      ))}
    </svg>
  );
}

/** FNV-1a 32비트 해시 — 결정적. */
function hashSeed(seed: string): number {
  let h = 2166136261;
  for (let i = 0; i < seed.length; i++) {
    h ^= seed.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}
