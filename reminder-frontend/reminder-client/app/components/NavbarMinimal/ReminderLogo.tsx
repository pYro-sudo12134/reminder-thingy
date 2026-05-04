"use client";
import React from 'react';

interface ReminderLogoProps {
  src: string;
  alt?: string;
  size?: number | string;
  className?: string;
  style?: React.CSSProperties;
}

export function ReminderLogo({ 
  src, 
  alt = "Reminder Logo", 
  size = 30, 
  className, 
  style 
}: ReminderLogoProps) {
  const dimension = typeof size === "number" ? `${size}px` : size;
  
  return (
    <img
      src={src}
      alt={alt}
      width={dimension}
      height={dimension}
      className={className}
      style={{ objectFit: "contain", ...style }}
    />
  );
}