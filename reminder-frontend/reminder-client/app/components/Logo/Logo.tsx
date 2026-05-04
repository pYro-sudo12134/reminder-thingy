import { SVGProps } from 'react';

export function Logo(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 40 40"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      {...props}
    >
      <circle cx="20" cy="20" r="20" fill="#FFFFFF" />
      
      <circle cx="20" cy="20" r="18" stroke="#228BE6" strokeWidth="3" fill="none" />
      
      <path
        d="M20 8C17.5 8 15.5 9.5 15.5 12V16C15.5 17.5 14.5 19 13 19.5V21H27V19.5C25.5 19 24.5 17.5 24.5 16V12C24.5 9.5 22.5 8 20 8Z"
        fill="#228BE6"
      />
      
      <circle cx="20" cy="23" r="2" fill="#228BE6" />
      
      <path
        d="M20 14V18L23 20"
        stroke="#FFFFFF"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
    </svg>
  );
}
