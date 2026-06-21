'use client'

import * as React from 'react'
import { cn } from '@/src/lib/utils'

// Minimal form components — shadcn/ui form primitives for admin-web
// Full react-hook-form integration added when needed in later plans

const Form = React.forwardRef<
  HTMLFormElement,
  React.FormHTMLAttributes<HTMLFormElement>
>(({ className, ...props }, ref) => (
  <form ref={ref} className={cn('space-y-4', className)} {...props} />
))
Form.displayName = 'Form'

const FormItem = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement>
>(({ className, ...props }, ref) => (
  <div ref={ref} className={cn('space-y-2', className)} {...props} />
))
FormItem.displayName = 'FormItem'

const FormMessage = React.forwardRef<
  HTMLParagraphElement,
  React.HTMLAttributes<HTMLParagraphElement>
>(({ className, children, ...props }, ref) => {
  if (!children) return null
  return (
    <p
      ref={ref}
      className={cn('text-sm font-medium text-destructive', className)}
      {...props}
    >
      {children}
    </p>
  )
})
FormMessage.displayName = 'FormMessage'

export { Form, FormItem, FormMessage }
