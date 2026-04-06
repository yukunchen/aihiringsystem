import { request } from './request';

export interface Department {
  id: string;
  name: string;
  parentId?: string;
  children?: Department[];
}

export async function listDepartments(): Promise<Department[]> {
  const tree = await request<Department[]>('/api/departments');
  return flattenDepartments(tree);
}

function flattenDepartments(departments: Department[]): Department[] {
  const result: Department[] = [];
  for (const dept of departments) {
    result.push({ id: dept.id, name: dept.name });
    if (dept.children?.length) {
      result.push(...flattenDepartments(dept.children));
    }
  }
  return result;
}
