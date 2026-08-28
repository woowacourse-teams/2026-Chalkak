import styles from "./page.module.css";

export default function Home() {
  return (
    <main className={styles.page}>
      <section className={styles.card}>
        <p className={styles.eyebrow}>CHALKAK ADMIN</p>
        <h1>관리자 웹 개발 기반</h1>
        <p>
          게시물 검수와 서비스 운영 기능을 안전하게 확장하기 위한 독립
          Next.js 애플리케이션입니다.
        </p>
      </section>
    </main>
  );
}
