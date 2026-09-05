import type { ReactNode } from "react";

import styles from "./common-ui.module.css";

export interface TableColumn<T> {
  id: string;
  header: string;
  render: (row: T) => ReactNode;
  align?: "left" | "center" | "right";
}

interface TableProps<T> {
  caption: string;
  columns: readonly TableColumn<T>[];
  getRowKey: (row: T) => string;
  rows: readonly T[];
}

export function Table<T>({
  caption,
  columns,
  getRowKey,
  rows,
}: TableProps<T>) {
  return (
    <div className={styles.tableScroll}>
      <table className={styles.table}>
        <caption className={styles.visuallyHidden}>{caption}</caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th
                className={styles[column.align ?? "left"]}
                key={column.id}
                scope="col"
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={getRowKey(row)}>
              {columns.map((column) => (
                <td
                  className={styles[column.align ?? "left"]}
                  key={column.id}
                >
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
