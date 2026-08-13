/**
 * 화이트보드 undo/redo 스택 — 각 사용자 행동을 undo/redo 클로저 쌍으로 기록한다.
 * 서버 mutation을 다시 쏘는 방식이라 로컬 상태 스냅샷이 필요 없고, 생성/삭제는 soft-delete
 * restore로 같은 id가 유지돼 이후 명령의 id 참조가 깨지지 않는다.
 */
export interface WbCommand {
  undo: () => void;
  redo: () => void;
}

const DEFAULT_LIMIT = 100;

export class WbHistory {
  private undoStack: WbCommand[] = [];
  private redoStack: WbCommand[] = [];
  private readonly limit: number;

  constructor(limit: number = DEFAULT_LIMIT) {
    this.limit = limit;
  }

  /** 새 행동 기록 — redo 스택은 비운다(타임라인 분기 방지). 한도 초과 시 가장 오래된 것부터 버린다. */
  push(cmd: WbCommand): void {
    this.undoStack.push(cmd);
    if (this.undoStack.length > this.limit) this.undoStack.shift();
    this.redoStack = [];
  }

  /** @returns 실행했으면 true, 스택이 비어 아무것도 안 했으면 false. */
  undo(): boolean {
    const cmd = this.undoStack.pop();
    if (!cmd) return false;
    this.redoStack.push(cmd);
    cmd.undo();
    return true;
  }

  redo(): boolean {
    const cmd = this.redoStack.pop();
    if (!cmd) return false;
    this.undoStack.push(cmd);
    cmd.redo();
    return true;
  }
}
