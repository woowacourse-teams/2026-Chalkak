import styles from "./section-placeholder.module.css";

interface SectionPlaceholderProps {
  eyebrow: string;
  title: string;
  description: string;
}

export function SectionPlaceholder({
  eyebrow,
  title,
  description,
}: SectionPlaceholderProps) {
  return (
    <section className={styles.placeholder}>
      <p>{eyebrow}</p>
      <h2>{title}</h2>
      <span>{description}</span>
      <div aria-hidden="true" className={styles.preview}>
        <i />
        <i />
        <i />
      </div>
    </section>
  );
}
