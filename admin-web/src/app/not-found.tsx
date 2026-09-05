import Link from "next/link";

import styles from "./special-page.module.css";

export default function NotFound() {
  return (
    <section className={styles.specialPage}>
      <p>404</p>
      <h2>요청한 관리 페이지를 찾을 수 없습니다.</h2>
      <span>주소가 정확한지 확인하거나 대시보드로 돌아가 주세요.</span>
      <Link href="/">대시보드로 이동</Link>
    </section>
  );
}
