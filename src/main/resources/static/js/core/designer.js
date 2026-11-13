/**
 * 课程设计要求：在程序运行界面突出显示设计者的班级、学号、姓名。
 *
 * 改这里即可，登录页与登录后的顶栏共用同一份信息。
 */
export const DESIGNER = {
  className: '25级计科99班',
  studentId: '3125000000',
  name: '宋公明',
};

export function designerText() {
  return `${DESIGNER.className} · ${DESIGNER.studentId} · ${DESIGNER.name}`;
}
