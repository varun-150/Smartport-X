import { expect, test } from '@playwright/test'

test.describe('Public login smoke', () => {
  test('renders the sign-in form without a backend', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', (err) => errors.push(String(err)))

    await page.goto('/login')

    await expect(page.getByRole('heading', { name: 'Terminal Operations' })).toBeVisible()
    await expect(page.getByText('Sign in with your terminal account.')).toBeVisible()
    await expect(page.getByLabel('Username')).toBeVisible()
    await expect(page.getByLabel('Password')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeEnabled()

    expect(errors).toEqual([])
  })

  test('shows client-side validation on an empty submit', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page.getByText('Username is required.')).toBeVisible()
    await expect(page.getByText('Password is required.')).toBeVisible()
  })
})